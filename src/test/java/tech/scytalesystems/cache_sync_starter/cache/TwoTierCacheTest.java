package tech.scytalesystems.cache_sync_starter.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.time.Duration;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 2329h
 */
@DisplayName("TwoTierCache Tests")
class TwoTierCacheTest {

    private Cache l1Cache;
    private Cache l2Cache;
    private TwoTierCache twoTierCache;

    @BeforeEach
    void setUp() {
        l1Cache = new CaffeineCache("test",
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .build());

        l2Cache = new ConcurrentMapCache("test");

        twoTierCache = new TwoTierCache("test", l1Cache, l2Cache);
    }

    @Test
    @DisplayName("Should write to both L1 and L2 caches")
    void testPut() {
        twoTierCache.put("key1", "value1");

        assertNotNull(l1Cache.get("key1"));
        assertNotNull(l2Cache.get("key1"));
        assertEquals("value1", Objects.requireNonNull(l1Cache.get("key1")).get());
        assertEquals("value1", Objects.requireNonNull(l2Cache.get("key1")).get());
    }

    @Test
    @DisplayName("Should read from L1 when available")
    void testGetFromL1() {
        l1Cache.put("key1", "value1");
        l2Cache.put("key1", "different-value");

        Cache.ValueWrapper result = twoTierCache.get("key1");

        assertNotNull(result);
        assertEquals("value1", result.get()); // L1 value, not L2
    }

    @Test
    @DisplayName("Should read from L2 and populate L1 on L1 miss")
    void testGetFromL2PopulatesL1() {
        // Only in L2
        l2Cache.put("key1", "value1");

        // First get should hit L2
        Cache.ValueWrapper result = twoTierCache.get("key1");

        assertNotNull(result);
        assertEquals("value1", result.get());

        // L1 should now be populated
        assertNotNull(l1Cache.get("key1"));
        assertEquals("value1", Objects.requireNonNull(l1Cache.get("key1")).get());
    }

    @Test
    @DisplayName("Should return null on cache miss")
    void testGetMiss() {
        Cache.ValueWrapper result = twoTierCache.get("nonexistent");

        assertNull(result);
    }

    @Test
    @DisplayName("Should evict from both L1 and L2")
    void testEvict() {
        twoTierCache.put("key1", "value1");

        twoTierCache.evict("key1");

        assertNull(l1Cache.get("key1"));
        assertNull(l2Cache.get("key1"));
    }

    @Test
    @DisplayName("Should clear both L1 and L2")
    void testClear() {
        twoTierCache.put("key1", "value1");
        twoTierCache.put("key2", "value2");

        twoTierCache.clear();

        assertNull(l1Cache.get("key1"));
        assertNull(l1Cache.get("key2"));
        assertNull(l2Cache.get("key1"));
        assertNull(l2Cache.get("key2"));
    }

    @Test
    @DisplayName("Should get typed value from L1")
    void testGetTyped() {
        twoTierCache.put("key1", "value1");

        String result = twoTierCache.get("key1", String.class);

        assertEquals("value1", result);
    }

    @Test
    @DisplayName("Should handle putIfAbsent correctly")
    void testPutIfAbsent() {
        twoTierCache.put("key1", "original");

        Cache.ValueWrapper result = twoTierCache.putIfAbsent("key1", "new-value");

        assertNotNull(result);
        assertEquals("original", result.get());
        assertEquals("original", Objects.requireNonNull(twoTierCache.get("key1")).get());
    }

    @Test
    @DisplayName("Should work with null L1 cache")
    void testNullL1Cache() {
        TwoTierCache cache = new TwoTierCache("test", null, l2Cache);

        cache.put("key1", "value1");

        assertNotNull(cache.get("key1"));
        assertEquals("value1", Objects.requireNonNull(cache.get("key1")).get());
    }

    @Test
    @DisplayName("Should work with null L2 cache")
    void testNullL2Cache() {
        TwoTierCache cache = new TwoTierCache("test", l1Cache, null);

        cache.put("key1", "value1");

        assertNotNull(cache.get("key1"));
        assertEquals("value1", Objects.requireNonNull(cache.get("key1")).get());
    }
}
