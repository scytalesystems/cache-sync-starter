package tech.scytalesystems.cache_sync_starter.cache;

import org.springframework.cache.Cache;
import org.springframework.lang.NonNull;

import java.util.concurrent.Callable;

/**
 * @param l1Cache Caffeine
 * @param l2Cache Redis
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1647h
 * <p>Cache implementation that uses two tiers:
 * <p>- L1 (Caffeine): Fast local cache
 * <p>- L2 (Redis): Distributed cache
 *
 * <p>Read strategy: L1 -> L2 (populate L1 on L2 hit)
 * <p>Write strategy: Write-through to both L1 and L2
 */
public record TwoTierCache(String name, Cache l1Cache, Cache l2Cache) implements Cache {
    @Override
    public @NonNull String getName() {
        return name;
    }

    @Override
    public @NonNull Object getNativeCache() {
        return this;
    }

    @Override
    public ValueWrapper get(@NonNull Object key) {
        // Try L1 first
        if (l1Cache != null) {
            ValueWrapper l1Value = l1Cache.get(key);

            if (l1Value != null) return l1Value;
        }

        // Try L2 on L1 miss
        if (l2Cache != null) {
            ValueWrapper l2Value = l2Cache.get(key);

            if (l2Value != null && l1Cache != null) l1Cache.put(key, l2Value.get()); // Populate L1 cache

            return l2Value;
        }

        return null;
    }

    @Override
    public <T> T get(@NonNull Object key, Class<T> type) {
        // Try L1 first
        if (l1Cache != null) {
            T l1Value = l1Cache.get(key, type);

            if (l1Value != null) return l1Value;
        }

        // Try L2 on L1 miss
        if (l2Cache != null) {
            T l2Value = l2Cache.get(key, type);

            if (l2Value != null && l1Cache != null) l1Cache.put(key, l2Value); // Populate L1 cache

            return l2Value;
        }

        return null;
    }

    @Override
    public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
        // Try L1 first
        if (l1Cache != null) {
            T l1Value = l1Cache.get(key, (Class<T>) null);

            if (l1Value != null) return l1Value;
        }

        // Try L2
        if (l2Cache != null) {
            T l2Value = l2Cache.get(key, (Class<T>) null);

            if (l2Value != null) {
                if (l1Cache != null) l1Cache.put(key, l2Value);

                return l2Value;
            }
        }

        // Load value and populate both caches
        try {
            T value = valueLoader.call();
            put(key, value);

            return value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(@NonNull Object key, Object value) {
        // Write-through to both caches
        if (l1Cache != null)  l1Cache.put(key, value);

        if (l2Cache != null) l2Cache.put(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(@NonNull Object key, Object value) {
        ValueWrapper existingValue = get(key);

        if (existingValue != null) return existingValue;

        put(key, value);
        return null;
    }

    @Override
    public void evict(@NonNull Object key) {
        // Evict from both caches
        if (l1Cache != null) l1Cache.evict(key);

        if (l2Cache != null) l2Cache.evict(key);
    }

    @Override
    public void clear() {
        // Clear both caches
        if (l1Cache != null) l1Cache.clear();

        if (l2Cache != null) l2Cache.clear();
    }
}
