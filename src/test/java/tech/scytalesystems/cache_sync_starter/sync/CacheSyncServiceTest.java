package tech.scytalesystems.cache_sync_starter.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import tech.scytalesystems.cache_sync_starter.config.CacheSyncProperties;
import tech.scytalesystems.cache_sync_starter.dto.CacheAction;
import tech.scytalesystems.cache_sync_starter.dto.CacheMessage;
import tech.scytalesystems.cache_sync_starter.util.JsonUtil;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 2331h
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CacheSyncService Tests")
class CacheSyncServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private RedisMessageListenerContainer container;

    private CacheSyncProperties props;
    private CacheSyncService cacheSyncService;

    @BeforeEach
    void setUp() {
        props = new CacheSyncProperties();
        props.setChannel("cache-invalidation");
        props.setChannelPrefix("");
        props.setCompressMessages(false);
        props.setCaffeineTtl(Duration.ofMinutes(5));
        props.setCaffeineMaxSize(1000L);
        props.setRedisTtl(Duration.ofMinutes(30));

        cacheSyncService = new CacheSyncService(redisTemplate, cacheManager, container, props);
    }

    @Test
    @DisplayName("Should subscribe to Redis channel on initialization")
    void testInitialization() {
        verify(container).addMessageListener(
                any(MessageListenerAdapter.class),
                eq(new ChannelTopic("cache-invalidation"))
        );
    }

    @Test
    @DisplayName("Should publish message to Redis")
    void testPublish() {
        CacheMessage message = CacheMessage.builder()
                .cacheName("users")
                .keys("user:1", "user:2")
                .action(CacheAction.EVICT)
                .build();

        cacheSyncService.publish(message);

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        verify(redisTemplate).convertAndSend(channelCaptor.capture(), messageCaptor.capture());

        assertEquals("cache-invalidation", channelCaptor.getValue());
        assertNotNull(messageCaptor.getValue());
        assertTrue(messageCaptor.getValue().contains("users"));
    }

    @Test
    @DisplayName("Should add instance ID to published message")
    void testPublishAddsInstanceId() {
        CacheMessage message = CacheMessage.builder()
                .cacheName("users")
                .keys("user:1")
                .action(CacheAction.EVICT)
                .build();

        cacheSyncService.publish(message);

        assertNotNull(message.getInstanceId());
        assertEquals(cacheSyncService.getInstanceId(), message.getInstanceId());
    }

    @Test
    @DisplayName("Should not publish during remote eviction")
    void testNoPublishDuringRemoteEviction() {
        // Simulate remote eviction flag being set
        Cache cache = new ConcurrentMapCache("users");
        when(cacheManager.getCache("users")).thenReturn(cache);

        CacheMessage message = CacheMessage.builder()
                .cacheName("users")
                .keys("user:1")
                .action(CacheAction.EVICT)
                .instanceId("different-instance")
                .build();

        String json = JsonUtil.toJson(message);
        cacheSyncService.onMessage(json, "cache-invalidation");

        // During onMessage, if we try to publish, it should be skipped
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    @DisplayName("Should ignore self-published messages")
    void testIgnoreSelfMessages() {
        Cache cache = new ConcurrentMapCache("users");
        cache.put("user:1", "data");
//        when(cacheManager.getCache("users")).thenReturn(cache);

        CacheMessage message = CacheMessage.builder()
                .cacheName("users")
                .keys("user:1")
                .action(CacheAction.EVICT)
                .instanceId(cacheSyncService.getInstanceId()) // Same instance
                .build();

        String json = JsonUtil.toJson(message);
        cacheSyncService.onMessage(json, "cache-invalidation");

        // Cache should not be evicted because it's from self
        assertNotNull(cache.get("user:1"));
    }

    @Test
    @DisplayName("Should process remote eviction message")
    void testProcessRemoteEviction() {
        Cache cache = new ConcurrentMapCache("users");
        cache.put("user:1", "data1");
        cache.put("user:2", "data2");
        when(cacheManager.getCache("users")).thenReturn(cache);

        CacheMessage message = CacheMessage.builder()
                .cacheName("users")
                .keys("user:1")
                .action(CacheAction.EVICT)
                .instanceId("different-instance")
                .build();

        String json = JsonUtil.toJson(message);
        cacheSyncService.onMessage(json, "cache-invalidation");

        assertNull(cache.get("user:1")); // Should be evicted
        assertNotNull(cache.get("user:2")); // Should remain
    }

    @Test
    @DisplayName("Should process remote clear message")
    void testProcessRemoteClear() {
        Cache cache = new ConcurrentMapCache("users");
        cache.put("user:1", "data1");
        cache.put("user:2", "data2");
        when(cacheManager.getCache("users")).thenReturn(cache);

        CacheMessage message = CacheMessage.builder()
                .cacheName("users")
                .action(CacheAction.CLEAR)
                .instanceId("different-instance")
                .build();

        String json = JsonUtil.toJson(message);
        cacheSyncService.onMessage(json, "cache-invalidation");

        assertNull(cache.get("user:1"));
        assertNull(cache.get("user:2"));
    }

    @Test
    @DisplayName("Should handle unknown cache gracefully")
    void testUnknownCache() {
        when(cacheManager.getCache("unknown")).thenReturn(null);

        CacheMessage message = CacheMessage.builder()
                .cacheName("unknown")
                .keys("key1")
                .action(CacheAction.EVICT)
                .instanceId("different-instance")
                .build();

        String json = JsonUtil.toJson(message);

        // Should not throw exception
        assertDoesNotThrow(() -> cacheSyncService.onMessage(json, "cache-invalidation"));
    }

    @Test
    @DisplayName("Should build channel name with prefix")
    void testChannelNameWithPrefix() {
        props.setChannelPrefix("prod:");

        CacheSyncService service = new CacheSyncService(
                redisTemplate, cacheManager, container, props);

        assertEquals("prod:cache-invalidation", service.getChannelName());
    }

    @Test
    @DisplayName("Should handle compressed messages")
    void testCompressedMessages() {
        props.setCompressMessages(true);
        CacheSyncService service = new CacheSyncService(
                redisTemplate, cacheManager, container, props);

        CacheMessage message = CacheMessage.builder()
                .cacheName("users")
                .keys("user:1", "user:2")
                .action(CacheAction.EVICT)
                .build();

        service.publish(message);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(anyString(), messageCaptor.capture());

        // Message should be Base64 (compressed)
        String published = messageCaptor.getValue();
        assertFalse(published.contains("users")); // Should be compressed, not plain JSON
    }

    @Test
    @DisplayName("Should have unique instance ID")
    void testUniqueInstanceId() {
        CacheSyncService service1 = new CacheSyncService(
                redisTemplate, cacheManager, container, props);
        CacheSyncService service2 = new CacheSyncService(
                redisTemplate, cacheManager, container, props);

        assertNotEquals(service1.getInstanceId(), service2.getInstanceId());
    }

    @Test
    @DisplayName("Should check remote eviction flag")
    void testRemoteEvictionFlag() {
        assertFalse(CacheSyncService.isRemoteEviction());

        // Simulate remote eviction processing
        Cache cache = new ConcurrentMapCache("test");
        when(cacheManager.getCache("test")).thenReturn(cache);

        CacheMessage message = CacheMessage.builder()
                .cacheName("test")
                .keys("key1")
                .action(CacheAction.EVICT)
                .instanceId("other-instance")
                .build();

        // During onMessage, the flag would be set
        String json = JsonUtil.toJson(message);
        cacheSyncService.onMessage(json, "test");

        // After processing, flag should be cleared
        assertFalse(CacheSyncService.isRemoteEviction());
    }
}
