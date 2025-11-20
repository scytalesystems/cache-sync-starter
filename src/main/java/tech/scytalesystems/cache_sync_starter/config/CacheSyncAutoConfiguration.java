package tech.scytalesystems.cache_sync_starter.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tech.scytalesystems.cache_sync_starter.aspect.CacheEvictAspect;
import tech.scytalesystems.cache_sync_starter.cache.TwoTierCacheManager;
import tech.scytalesystems.cache_sync_starter.sync.CacheSyncService;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1316h
 * <p>Spring Boot AutoConfiguration that:
 * <p>- Provides two-tier CacheManager combining Caffeine (L1) + Redis (L2)
 * <p>- Registers RedisTemplate, listener container, and CacheSyncService
 * <p>- Sets up automatic @CacheEvict interception via AOP aspect
 * <p>This configuration is activated when:
 * <p>1. Redis classes are on the classpath
 * <p>2. RedisConnectionFactory bean exists
 * <p>3. app.cache.sync.enabled=true (default)
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnBean(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "app.cache.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CacheSyncProperties.class)
@SuppressWarnings("unused")
public class CacheSyncAutoConfiguration {
    /**
     * Creates Caffeine (L1) cache manager.
     * Configured with TTL and max size from properties.
     */
    @Bean
    @ConditionalOnMissingBean(name = "caffeineCacheManager")
    public CaffeineCacheManager caffeineCacheManager(CacheSyncProperties props) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(props.getCaffeineMaxSize())
                .expireAfterWrite(props.getCaffeineTtl()));

        return cacheManager;
    }

    /**
     * Creates Redis (L2) cache manager.
     * Configured with TTL and JSON serialization from properties.
     */
    @Bean
    @ConditionalOnMissingBean(name = "redisCacheManager")
    public RedisCacheManager redisCacheManager(CacheSyncProperties props, RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration redisCfg = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(props.getRedisTtl())
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(redisCfg)
                .build();
    }

    /**
     * Creates two-tier cache manager that combines Caffeine (L1) and Redis (L2).
     * <p>
     * Cache flow:
     * - Read: L1 → L2 → Database (populates L1 on L2 hit)
     * - Write: Updates both L1 and L2
     * - Evict: Removes from both L1 and L2
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager(CaffeineCacheManager caffeineCacheManager, RedisCacheManager redisCacheManager) {
        return new TwoTierCacheManager(caffeineCacheManager, redisCacheManager);
    }

    /**
     * Creates RedisTemplate for pub/sub messaging.
     * Configured with String serializers for both key and value.
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Creates Redis message listener container for pub/sub.
     * Container lifecycle is managed by Spring (destroyMethod).
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        return container;
    }

    /**
     * Creates cache synchronization service.
     * Handles publishing and receiving cache invalidation messages via Redis pub/sub.
     * Automatically subscribes to the configured channel on initialization.
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheSyncService cacheSyncService(RedisTemplate<String, String> redisTemplate,
                                             CacheManager cacheManager,
                                             RedisMessageListenerContainer container,
                                             CacheSyncProperties props) {
        return new CacheSyncService(redisTemplate, cacheManager, container, props);
    }

    /**
     * Creates AOP aspect for intercepting @CacheEvict annotations.
     * Automatically publishes cache invalidation messages when @CacheEvict is triggered.
     * <p>
     * Note: Requires @EnableAspectJAutoProxy in your application configuration.
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheEvictAspect cacheEvictAspect(CacheSyncService cacheSyncService) {
        return new CacheEvictAspect(cacheSyncService);
    }

    @Bean
    public RedisSerializer<Object> redisSerializer() {
        return new GenericJackson2JsonRedisSerializer(new JacksonConfig().objectMapper());
    }
}