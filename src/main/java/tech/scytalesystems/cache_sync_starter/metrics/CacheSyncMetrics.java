package tech.scytalesystems.cache_sync_starter.metrics;

import tech.scytalesystems.cache_sync_starter.dto.CacheAction;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1310h
 * <p>
 * Optional interface for collecting metrics related to cache synchronization.
 * Implement this interface as a Spring bean to automatically enable metrics collection.
 */
public interface CacheSyncMetrics {
    /**
     * Called when a cache invalidation message is successfully published.
     *
     * @param cacheName The name of the cache.
     * @param action    The action performed (e.g., EVICT, CLEAR).
     * @param keyCount  The number of keys affected.
     */
    void recordPublish(String cacheName, CacheAction action, int keyCount);

    /**
     * Called when a cache invalidation message is received from a remote instance.
     *
     * @param cacheName The name of the cache.
     * @param action    The action performed (e.g., EVICT, CLEAR).
     * @param keyCount  The number of keys affected.
     */
    void recordReceive(String cacheName, CacheAction action, int keyCount);

    /**
     * Called when an error occurs during message publishing or processing.
     *
     * @param cacheName The name of the cache, or null if not available.
     * @param error     The exception that occurred.
     */
    void recordError(String cacheName, Throwable error);

    /**
     * Called when a message is ignored because it was published by the same instance.
     */
    void recordSelfMessageIgnored();
}
