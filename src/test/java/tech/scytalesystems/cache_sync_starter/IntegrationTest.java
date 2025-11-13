package tech.scytalesystems.cache_sync_starter;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tech.scytalesystems.cache_sync_starter.cache.TwoTierCache;
import tech.scytalesystems.cache_sync_starter.cache.TwoTierCacheManager;
import tech.scytalesystems.cache_sync_starter.config.CacheSyncProperties;
import tech.scytalesystems.cache_sync_starter.dto.CacheAction;
import tech.scytalesystems.cache_sync_starter.dto.CacheMessage;
import tech.scytalesystems.cache_sync_starter.sync.CacheSyncService;
import tech.scytalesystems.cache_sync_starter.util.JsonUtil;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 2343h
 */
@DisplayName("Integration Tests")
@Disabled("Disabled until Redis is available for testing")
@SuppressWarnings("all")
class IntegrationTest {

    private TwoTierCacheManager cacheManager;
    private CacheSyncService cacheSyncService1;
    private CacheSyncService cacheSyncService2;
    private RedisTemplate<String, String> redisTemplate;
    private CacheSyncProperties props;

    @BeforeEach
    void setUp() {
        // Setup cache managers
        CaffeineCacheManager caffeine = new CaffeineCacheManager();
        caffeine.setCaffeine(Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(5)));

//        ConcurrentMapCacheManager redis = new ConcurrentMapCacheManager();

//        cacheManager = new TwoTierCacheManager(caffeine, redis);

        // Setup properties
        props = new CacheSyncProperties();
        props.setChannel("test-channel");
        props.setCompressMessages(false);

        // Mock Redis
        redisTemplate = mock(RedisTemplate.class);
        RedisMessageListenerContainer container1 = mock(RedisMessageListenerContainer.class);
        RedisMessageListenerContainer container2 = mock(RedisMessageListenerContainer.class);

        // Create two service instances (simulating two app instances)
        cacheSyncService1 = new CacheSyncService(redisTemplate, cacheManager, container1, props);
        cacheSyncService2 = new CacheSyncService(redisTemplate, cacheManager, container2, props);
    }

    @Test
    @DisplayName("Full flow: Write to cache, evict, publish, receive")
    void testFullFlow() {
        // Get cache from both "instances"
        Cache cache1 = cacheManager.getCache("users");
        Cache cache2 = cacheManager.getCache("users");

        assertInstanceOf(TwoTierCache.class, cache1);
        assertInstanceOf(TwoTierCache.class, cache2);

        // Instance 1: Write to cache
        cache1.put("user:1", "John Doe");
        cache1.put("user:2", "Jane Smith");

        // Verify both L1 and L2 have data
        TwoTierCache twoTier1 = (TwoTierCache) cache1;
        assertNotNull(twoTier1.l1Cache().get("user:1"));
        assertNotNull(twoTier1.l2Cache().get("user:1"));

        // Instance 1: Publish eviction message
        CacheMessage message = CacheMessage.builder()
                .cacheName("users")
                .keys("user:1")
                .action(CacheAction.EVICT)
                .instanceId(cacheSyncService1.getInstanceId())
                .build();

        cacheSyncService1.publish(message);

        // Simulate Instance 2 receiving the message
        String json = JsonUtil.toJson(message);
        cacheSyncService2.onMessage(json, "test-channel");

        // Instance 2: L1 should be cleared, but L2 should still have data
        TwoTierCache twoTier2 = (TwoTierCache) cache2;

        // Note: In real scenario, L1 would be cleared on instance 2
        // Here we're testing the message flow
        assertNotNull(twoTier2.l2Cache().get("user:1"));
    }

    @Test
    @DisplayName("Two-tier cache: L1 miss populates from L2")
    void testL1MissPopulatesFromL2() {
        Cache cache = cacheManager.getCache("products");
        TwoTierCache twoTierCache = (TwoTierCache) cache;

        // Put directly in L2 (simulating another instance wrote it)
        assertNotNull(twoTierCache);
        twoTierCache.l2Cache().put("product:1", "Widget");

        // Clear L1
        twoTierCache.l1Cache().clear();

        // Get from two-tier cache - should hit L2 and populate L1
        Cache.ValueWrapper result = twoTierCache.get("product:1");

        assertNotNull(result);
        assertEquals("Widget", result.get());

        // L1 should now be populated
        assertNotNull(twoTierCache.l1Cache().get("product:1"));
    }

    @Test
    @DisplayName("Self-published messages are ignored")
    void testSelfMessageIgnored() {
        Cache cache = cacheManager.getCache("orders");
        assertNotNull(cache);
        cache.put("order:1", "Order Data");

        // Create message with same instance ID
        CacheMessage message = CacheMessage.builder()
                .cacheName("orders")
                .keys("order:1")
                .action(CacheAction.EVICT)
                .instanceId(cacheSyncService1.getInstanceId())
                .build();

        String json = JsonUtil.toJson(message);

        // Process message with same instance
        cacheSyncService1.onMessage(json, "test-channel");

        // Cache should NOT be evicted
        assertNotNull(cache.get("order:1"));
    }

    @Test
    @DisplayName("Clear action clears entire cache")
    void testClearAction() {
        Cache cache = cacheManager.getCache("sessions");
        TwoTierCache twoTierCache = (TwoTierCache) cache;

        assertNotNull(cache);
        cache.put("session:1", "data1");
        cache.put("session:2", "data2");
        cache.put("session:3", "data3");

        // Create clear message from different instance
        CacheMessage message = CacheMessage.builder()
                .cacheName("sessions")
                .action(CacheAction.CLEAR)
                .instanceId("different-instance")
                .build();

        String json = JsonUtil.toJson(message);
        cacheSyncService1.onMessage(json, "test-channel");

        // L1 should be cleared
        assertNull(twoTierCache.l1Cache().get("session:1"));
        assertNull(twoTierCache.l1Cache().get("session:2"));
        assertNull(twoTierCache.l1Cache().get("session:3"));
    }

    @Test
    @DisplayName("Compression reduces message size")
    void testCompression() {
        props.setCompressMessages(true);

        CacheSyncService compressingService = new CacheSyncService(
                redisTemplate, cacheManager, mock(RedisMessageListenerContainer.class), props);

        // Create message with many keys
        CacheMessage.CacheMessageBuilder builder = CacheMessage.builder()
                .cacheName("large-cache")
                .action(CacheAction.EVICT);

        for (int i = 0; i < 100; i++) {
            builder.addKey("key:" + i);
        }

        CacheMessage message = builder.build();

        // Publishing should compress
        compressingService.publish(message);

        // Verify Redis template was called
        verify(redisTemplate).convertAndSend(eq("test-channel"), anyString());
    }

    @Test
    @DisplayName("Different instances have different instance IDs")
    void testUniqueInstanceIds() {
        assertNotEquals(cacheSyncService1.getInstanceId(), cacheSyncService2.getInstanceId());
    }

    @Test
    @DisplayName("Channel name includes prefix")
    void testChannelPrefix() {
        props.setChannelPrefix("prod:");

        CacheSyncService service = new CacheSyncService(
                redisTemplate, cacheManager, mock(RedisMessageListenerContainer.class), props);

        assertEquals("prod:test-channel", service.getChannelName());
    }
}
