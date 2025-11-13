package tech.scytalesystems.cache_sync_starter.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1645h
 * <p>Two-tier cache manager that provides L1 (Caffeine) + L2 (Redis) caching.
 * <p>When getting a cache, wraps both tiers in a TwoTierCache.
 */
public record TwoTierCacheManager(
        CaffeineCacheManager caffeineCacheManager,
        RedisCacheManager redisCacheManager) implements CacheManager {
    @Override
    public Cache getCache(@NonNull String name) {
        Cache l1 = caffeineCacheManager.getCache(name);
        Cache l2 = redisCacheManager.getCache(name);

        if (l1 == null && l2 == null) return null;

        return new TwoTierCache(name, l1, l2);
    }

    @Override
    public @NonNull Collection<String> getCacheNames() {
        return Stream.concat(caffeineCacheManager.getCacheNames().stream(), redisCacheManager.getCacheNames().stream())
                .distinct()
                .toList();
    }
}
