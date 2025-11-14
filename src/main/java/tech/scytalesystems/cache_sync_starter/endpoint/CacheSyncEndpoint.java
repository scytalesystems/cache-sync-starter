package tech.scytalesystems.cache_sync_starter.endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import tech.scytalesystems.cache_sync_starter.cache.TwoTierCache;
import tech.scytalesystems.cache_sync_starter.config.CacheSyncProperties;
import tech.scytalesystems.cache_sync_starter.dto.CacheAction;
import tech.scytalesystems.cache_sync_starter.dto.CacheMessage;
import tech.scytalesystems.cache_sync_starter.sync.CacheSyncService;

import java.time.Instant;
import java.util.*;

/**
 * @author Gathariki Ngigi
 * Created on 14/11/2025
 * Time 1108h
 * <p>
 * Spring Boot Actuator endpoint for cache synchronization management and monitoring.
 * <p>
 * Access via: /actuator/cache-sync
 * <p>
 * Features:
 * - View cache synchronization status and configuration
 * - List all caches and their statistics
 * - Manually trigger cache evictions across all instances
 * - View instance information
 * - Monitor cache health
 * <p>
 * Requires spring-boot-starter-actuator dependency.
 * <p>
 * Configuration:
 * management.endpoints.web.exposure.include=cache-sync
 * management.endpoint.cache-sync.enabled=true
 *
 * @author Gathariki Ngigi
 */
@Component
@Endpoint(id = "cache-sync")
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
@SuppressWarnings("all")
public class CacheSyncEndpoint {
    private static final Logger log = LoggerFactory.getLogger(CacheSyncEndpoint.class);

    private final CacheSyncService cacheSyncService;
    private final CacheManager cacheManager;
    private final CacheSyncProperties properties;
    private final Instant startupTime;

    public CacheSyncEndpoint(CacheSyncService cacheSyncService, CacheManager cacheManager, CacheSyncProperties properties) {
        this.cacheSyncService = cacheSyncService;
        this.cacheManager = cacheManager;
        this.properties = properties;
        this.startupTime = Instant.now();
    }

    /**
     * GET /actuator/cache-sync
     * <p>
     * Returns comprehensive cache sync status and configuration.
     * <p>
     * Response includes:
     * - Instance information (ID, channel, uptime)
     * - Configuration settings
     * - List of all caches with statistics
     * - Cache sync health status
     */
    @ReadOperation
    public Map<String, Object> info() {
        Map<String, Object> result = new HashMap<>();

        // Instance information
        Map<String, Object> instance = new HashMap<>();
        instance.put("instanceId", cacheSyncService.getInstanceId());
        instance.put("channel", cacheSyncService.getChannelName());
        instance.put("startupTime", startupTime);
        instance.put("uptime", calculateUptime());
        result.put("instance", instance);

        // Configuration
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", properties.isEnabled());
        config.put("channelPrefix", properties.getChannelPrefix());
        config.put("channel", properties.getChannel());
        config.put("compressMessages", properties.isCompressMessages());
        config.put("caffeineTtl", properties.getCaffeineTtl().toString());
        config.put("caffeineMaxSize", properties.getCaffeineMaxSize());
        config.put("redisTtl", properties.getRedisTtl().toString());
        result.put("configuration", config);

        // Cache statistics
        Collection<String> cacheNames = cacheManager.getCacheNames();
        List<Map<String, Object>> caches = cacheNames.stream()
                .map(this::getCacheInfo)
                .toList();
        result.put("caches", caches);
        result.put("cacheCount", caches.size());

        // Health status
        result.put("status", determineHealthStatus());

        return result;
    }

    /**
     * GET /actuator/cache-sync/caches
     * Returns detailed information about all caches.
     */
    @ReadOperation
    public Map<String, Object> caches() {
        Collection<String> cacheNames = cacheManager.getCacheNames();

        Map<String, Object> result = new HashMap<>();
        result.put("count", cacheNames.size());
        result.put("names", cacheNames);

        List<Map<String, Object>> details = cacheNames.stream()
                .map(this::getCacheInfo)
                .toList();
        result.put("details", details);

        return result;
    }

    /**
     * POST /actuator/cache-sync/evict
     * <p>
     * Manually evicts specific keys from a cache across all instances.
     * <p>
     * Request body:
     * {
     *   "cacheName": "users",
     *   "keys": ["user:1", "user:2"]
     * }
     * <p>
     * Response:
     * {
     *   "success": true,
     *   "cacheName": "users",
     *   "keysEvicted": 2,
     *   "publishedAt": "2025-11-15T10:30:00Z"
     * }
     */
    @WriteOperation
    public Map<String, Object> evict(String cacheName, List<String> keys) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Validate cache exists
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                result.put("success", false);
                result.put("error", "Cache not found: " + cacheName);
                result.put("availableCaches", cacheManager.getCacheNames());
                return result;
            }

            // Validate keys provided
            if (keys == null || keys.isEmpty()) {
                result.put("success", false);
                result.put("error", "No keys provided for eviction");
                return result;
            }

            // Build and publish eviction message
            CacheMessage message = CacheMessage.builder()
                    .cacheName(cacheName)
                    .keys(keys)
                    .action(CacheAction.EVICT)
                    .build();

            cacheSyncService.publish(message);

            // Evict locally
            keys.forEach(cache::evict);

            result.put("success", true);
            result.put("cacheName", cacheName);
            result.put("keysEvicted", keys.size());
            result.put("keys", keys);
            result.put("publishedAt", Instant.now());
            result.put("instanceId", cacheSyncService.getInstanceId());

            log.info("Manual eviction triggered via endpoint: cache={}, keys={}", cacheName, keys.size());

        } catch (Exception e) {
            log.error("Failed to evict cache via endpoint: cache={}", cacheName, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * POST /actuator/cache-sync/clear
     * <p>
     * Clears an entire cache across all instances.
     * <p>
     * Request body:
     * {
     *   "cacheName": "users"
     * }
     * <p>
     * Response:
     * {
     *   "success": true,
     *   "cacheName": "users",
     *   "action": "CLEAR",
     *   "publishedAt": "2025-11-15T10:30:00Z"
     * }
     */
    @WriteOperation
    public Map<String, Object> clear(String cacheName) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Validate cache exists
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                result.put("success", false);
                result.put("error", "Cache not found: " + cacheName);
                result.put("availableCaches", cacheManager.getCacheNames());
                return result;
            }

            // Build and publish clear message
            CacheMessage message = CacheMessage.builder()
                    .cacheName(cacheName)
                    .action(CacheAction.CLEAR)
                    .keys(new ArrayList<>())
                    .build();

            cacheSyncService.publish(message);

            // Clear locally
            cache.clear();

            result.put("success", true);
            result.put("cacheName", cacheName);
            result.put("action", "CLEAR");
            result.put("publishedAt", Instant.now());
            result.put("instanceId", cacheSyncService.getInstanceId());

            log.warn("Manual cache clear triggered via endpoint: cache={}", cacheName);

        } catch (Exception e) {
            log.error("Failed to clear cache via endpoint: cache={}", cacheName, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * POST /actuator/cache-sync/clear-all
     * <p>
     * Clears ALL caches across all instances.
     * Use with caution in production!
     * <p>
     * Response:
     * {
     *   "success": true,
     *   "cachesCleared": 5,
     *   "cacheNames": ["users", "products", "orders", "sessions", "configs"],
     *   "publishedAt": "2025-11-15T10:30:00Z"
     * }
     */
    @WriteOperation
    public Map<String, Object> clearAll() {
        Map<String, Object> result = new HashMap<>();

        try {
            Collection<String> cacheNames = cacheManager.getCacheNames();
            List<String> clearedCaches = new ArrayList<>();

            for (String cacheName : cacheNames) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    // Build and publish clear message
                    CacheMessage message = CacheMessage.builder()
                            .cacheName(cacheName)
                            .action(CacheAction.CLEAR)
                            .keys(new ArrayList<>())
                            .build();

                    cacheSyncService.publish(message);
                    cache.clear();
                    clearedCaches.add(cacheName);
                }
            }

            result.put("success", true);
            result.put("cachesCleared", clearedCaches.size());
            result.put("cacheNames", clearedCaches);
            result.put("publishedAt", Instant.now());
            result.put("instanceId", cacheSyncService.getInstanceId());

            log.warn("Manual clear-all triggered via endpoint: caches={}", clearedCaches.size());
        } catch (Exception e) {
            log.error("Failed to clear all caches via endpoint", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Extracts information about a specific cache.
     * @param cacheName the cache name
     */
    private Map<String, Object> getCacheInfo(String cacheName) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", cacheName);

        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                info.put("type", cache.getClass().getSimpleName());

                // If it's a TwoTierCache, provide L1/L2 details
                if (cache instanceof TwoTierCache twoTierCache) {
                    Map<String, Object> tiers = new HashMap<>();

                    Cache l1 = twoTierCache.l1Cache();
                    if (l1 != null) {
                        Map<String, Object> l1Info = new HashMap<>();
                        l1Info.put("type", l1.getClass().getSimpleName());
                        l1Info.put("nativeCache", l1.getNativeCache().getClass().getSimpleName());
                        tiers.put("l1", l1Info);
                    }

                    Cache l2 = twoTierCache.l2Cache();
                    if (l2 != null) {
                        Map<String, Object> l2Info = new HashMap<>();
                        l2Info.put("type", l2.getClass().getSimpleName());
                        l2Info.put("nativeCache", l2.getNativeCache().getClass().getSimpleName());
                        tiers.put("l2", l2Info);
                    }

                    info.put("tiers", tiers);
                }

                info.put("nativeCache", cache.getNativeCache().getClass().getSimpleName());
            } else {
                info.put("error", "Cache not found");
            }
        } catch (Exception e) {
            info.put("error", e.getMessage());
        }

        return info;
    }

    /**
     * Calculates uptime in human-readable format.
     */
    private String calculateUptime() {
        long uptimeMillis = System.currentTimeMillis() - startupTime.toEpochMilli();
        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Determines overall health status of cache sync.
     */
    private String determineHealthStatus() {
        try {
            // Check if configuration is valid
            if (!properties.isEnabled()) return "DISABLED";

            // Check if cache manager is accessible
            Collection<String> cacheNames = cacheManager.getCacheNames();
            if (cacheNames.isEmpty()) return "WARNING: No caches configured";

            // Check if Redis channel is configured
            if (cacheSyncService.getChannelName() == null || cacheSyncService.getChannelName().isEmpty()) {
                return "ERROR: Invalid channel configuration";
            }

            return "UP";
        } catch (Exception e) {
            log.error("Error determining health status", e);
            return "ERROR: " + e.getMessage();
        }
    }
}