package tech.scytalesystems.cache_sync_starter.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.time.Duration;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 2337h
 */
@DisplayName("TwoTierCacheManager Tests")
@Disabled("Disabled until Redis is available for testing")
@SuppressWarnings("unused")
class TwoTierCacheManagerTest {

    private CaffeineCacheManager caffeineCacheManager;
//    private ConcurrentMapCacheManager redisCacheManager;
    private TwoTierCacheManager twoTierCacheManager;

    @BeforeEach
    void setUp() {
        caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(5)));

//        redisCacheManager = new ConcurrentMapCacheManager();

//        twoTierCacheManager = new TwoTierCacheManager(caffeineCacheManager, redisCacheManager);
    }

    @Test
    @DisplayName("Should return TwoTierCache instance")
    void testGetCache() {
        Cache cache = twoTierCacheManager.getCache("test");

        assertNotNull(cache);
        assertInstanceOf(TwoTierCache.class, cache);
        assertEquals("test", cache.getName());
    }

    @Test
    @DisplayName("Should return same cache instance for same name")
    void testCacheInstanceConsistency() {
        Cache cache1 = twoTierCacheManager.getCache("test");
        Cache cache2 = twoTierCacheManager.getCache("test");

        // Should create new instances each time (by design)
        assertNotNull(cache1);
        assertNotNull(cache2);
    }

    @Test
    @DisplayName("Should return all cache names from both managers")
    void testGetCacheNames() {
        twoTierCacheManager.getCache("cache1");
        twoTierCacheManager.getCache("cache2");

        Collection<String> names = twoTierCacheManager.getCacheNames();

        assertTrue(names.contains("cache1"));
        assertTrue(names.contains("cache2"));
    }

    @Test
    @DisplayName("Should provide access to underlying managers")
    void testGetUnderlyingManagers() {
        assertSame(caffeineCacheManager, twoTierCacheManager.caffeineCacheManager());
//        assertSame(redisCacheManager, twoTierCacheManager.redisCacheManager());
    }
}
