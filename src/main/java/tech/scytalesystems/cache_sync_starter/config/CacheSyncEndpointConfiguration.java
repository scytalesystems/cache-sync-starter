package tech.scytalesystems.cache_sync_starter.config;

import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.scytalesystems.cache_sync_starter.endpoint.CacheSyncEndpoint;
import tech.scytalesystems.cache_sync_starter.sync.CacheSyncService;

/**
 * @author Gathariki Ngigi
 * Created on 14/11/2025
 * Time 1113h
 * Auto-configuration for CacheSyncEndpoint Spring Boot Actuator endpoint.
 * <p>
 * This configuration automatically registers the cache-sync endpoint when:
 * 1. Spring Boot Actuator is on the classpath
 * 2. The Endpoint annotation class is available
 * 3. The endpoint is enabled in configuration
 * 4. No custom CacheSyncEndpoint bean is already defined
 * <p>
 * The endpoint provides:
 * - Cache synchronization monitoring and statistics
 * - Manual cache eviction/clearing across all instances
 * - Health status and diagnostics
 * - Instance and configuration information
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {
        "org.springframework.boot.actuate.endpoint.annotation.Endpoint",
        "org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint"
})
@SuppressWarnings("unused")
public class CacheSyncEndpointConfiguration {
    /**
     * Creates the CacheSyncEndpoint bean if:
     * - No custom CacheSyncEndpoint bean exists
     * - The endpoint is available (Actuator is present and endpoint is enabled)
     * <p>
     * The endpoint will be registered at /actuator/cache-sync
     *
     * @param cacheSyncService Service for cache synchronization
     * @param cacheManager Cache manager (typically TwoTierCacheManager)
     * @param properties Cache sync configuration properties
     * @return CacheSyncEndpoint instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnAvailableEndpoint
    public CacheSyncEndpoint cacheSyncEndpoint(CacheSyncService cacheSyncService,
                                               CacheManager cacheManager,
                                               CacheSyncProperties properties) {
        return new CacheSyncEndpoint(cacheSyncService, cacheManager, properties);
    }
}