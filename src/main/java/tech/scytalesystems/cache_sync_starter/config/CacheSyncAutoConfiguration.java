package tech.scytalesystems.cache_sync_starter.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tech.scytalesystems.cache_sync_starter.aspect.CacheEvictAspect;
import tech.scytalesystems.cache_sync_starter.sync.CacheSyncService;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1316h
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.cache.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CacheSyncProperties.class)
public class CacheSyncAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public CacheManager cacheManager(CacheSyncProperties props, RedisConnectionFactory redisConnectionFactory) {
        // Caffeine local
        CaffeineCacheManager caffeine = new CaffeineCacheManager();
        caffeine.setCaffeine(Caffeine.newBuilder()
                .maximumSize(props.getCaffeineMaxSize())
                .expireAfterWrite(props.getCaffeineTtl()));

        // Redis cache manager
        RedisCacheConfiguration redisCfg = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(props.getRedisTtl())
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        RedisCacheManager redis = RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(redisCfg)
                .build();

        // Composite: caffeine first, then redis
        CompositeCacheManager composite = new CompositeCacheManager(caffeine, redis);
        composite.setFallbackToNoOpCache(false);

        return composite;
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());

        return template;
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        return container;
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheSyncService cacheSyncService(RedisTemplate<String, String> redisTemplate,
                                             CacheManager cacheManager,
                                             RedisMessageListenerContainer container,
                                             CacheSyncProperties props) {
        return new CacheSyncService(redisTemplate, cacheManager, container, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheEvictAspect cacheEvictAspect(CacheSyncService cacheSyncService) {
        return new CacheEvictAspect(cacheSyncService);
    }
}
