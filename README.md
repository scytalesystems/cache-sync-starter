# Cache Sync Starter

[![Maven Central](https://img.shields.io/badge/maven--central-0.1.0-blue.svg)](https://search.maven.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)

A production-ready Spring Boot starter providing hybrid caching (Caffeine + Redis) with automatic cache invalidation 
propagation across distributed service instances via Redis Pub/Sub.

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Usage Examples](#usage-examples)
- [Actuator Endpoint](#actuator-endpoint)
- [Testing](#testing)
- [Advanced Configuration](#advanced-configuration)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Hybrid Caching**: Local Caffeine cache for ultra-fast access + Redis for distributed persistence
- **Automatic Synchronization**: Cache invalidations automatically propagate across all service instances
- **SpEL Support**: Full Spring Expression Language support in `@CacheEvict` annotations
- **Zero Configuration**: Works out-of-the-box with sensible defaults
- **Message Compression**: Optional Gzip compression for large cache messages
- **Environment Isolation**: Channel prefixing for multi-environment deployments
- **Production Monitoring**: Built-in Actuator endpoint for cache operations visibility
- **Battle-Tested**: Comprehensive test suite including Testcontainers integration tests

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Service Instance 1                          │
│  ┌──────────────┐         ┌─────────────────┐                   │
│  │ @CacheEvict  │────────>│ CacheEvictAspect│                   │
│  └──────────────┘         └────────┬────────┘                   │
│                                     │                           │
│                                     v                           │
│                          ┌─────────────────┐                    │
│  ┌──────────────┐        │ CacheSyncService│                    │
│  │   Caffeine   │<───────┤   (Publisher)   │                    │
│  │ Local Cache  │        └────────┬────────┘                    │
│  └──────────────┘                 │                             │
└───────────────────────────────────┼─────────────────────────────┘
                                    │
                                    v
                        ┌────────────────────────┐
                        │    Redis Pub/Sub       │
                        │  cache-invalidation    │
                        └────────────┬───────────┘
                                     │
                                     v
┌────────────────────────────────────┼────────────────────────────┐
│                     Service Instance 2                          │
│                                    │                            │
│                          ┌─────────v───────┐                    │
│  ┌──────────────┐        │ CacheSyncService│                    │
│  │   Caffeine   │<───────┤  (Subscriber)   │                    │
│  │ Local Cache  │        └─────────────────┘                    │
│  └──────────────┘                                               │
└─────────────────────────────────────────────────────────────────┘
```

### How It Works

1. **Cache Operation**: Application invokes `@CacheEvict` on a cached method
2. **Aspect Interception**: `CacheEvictAspect` intercepts the annotation
3. **SpEL Resolution**: Cache keys are resolved using Spring Expression Language
4. **Local Eviction**: Local Caffeine cache is cleared immediately
5. **Message Publishing**: `CacheMessage` published to Redis Pub/Sub channel
6. **Broadcast**: All subscribed service instances receive the message
7. **Remote Eviction**: Each instance evicts matching entries from local cache
8. **Consistency**: All instances maintain synchronized cache state

## Installation

### Maven

Add the starter dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>tech.scytalesystems</groupId>
    <artifactId>cache-sync-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'tech.scytalesystems:cache-sync-starter:1.0.0'
```

## Quick Start

### 1. Enable Caching

Add `@EnableCaching` to your main application class:

```java
@SpringBootApplication
@EnableCaching
public class MyApplication {
    static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### 2. Configure Redis Connection

Add Redis configuration to `application.yml`:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}  # Optional
      timeout: 2000ms
```

### 3. Use Cache Annotations

```java
@Service
public class CountryService {
    
    @Cacheable(value = "countries", key = "#id")
    public Country getCountryById(String id) {
        // This result will be cached locally and in Redis
        return countryRepository.findById(id);
    }
    
    @CacheEvict(value = "countries", key = "#country.id")
    public void updateCountry(Country country) {
        countryRepository.save(country);
        // Cache eviction automatically syncs across all instances
    }
    
    @CacheEvict(value = "countries", allEntries = true)
    public void clearAllCountries() {
        // Clears entire cache across all instances
    }
}
```

That's it! Your cache is now synchronized across all service instances.

## Configuration

### Default Configuration

The starter works with zero configuration, using these defaults:

```yaml
app:
  cache:
    sync:
      enabled: true                    # Enable/disable cache sync
      channel: cache-invalidation      # Redis Pub/Sub channel name
      channel-prefix: ""               # Environment prefix (e.g., "prod:")
      compress-messages: false         # Enable Gzip compression
      caffeine-ttl: 5m                 # Local cache TTL
      caffeine-max-size: 1000          # Max local cache entries
      redis-ttl: 30m                   # Redis cache TTL
```

### Production Configuration Example

```yaml
app:
  cache:
    sync:
      enabled: true
      channel-prefix: "${APP_ENV:prod}:"  # Isolate by environment
      channel: cache-invalidation
      compress-messages: true              # Enable for large messages
      caffeine-ttl: 10m
      caffeine-max-size: 5000
      redis-ttl: 1h

spring:
  data:
    redis:
      host: redis-cluster.production.com
      port: 6379
      password: ${REDIS_PASSWORD}
      ssl: true
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,cache-sync
```

### Configuration Properties Reference

| Property                           | Type     | Default              | Description                                 |
|------------------------------------|----------|----------------------|---------------------------------------------|
| `aml.cache.sync.enabled`           | Boolean  | `true`               | Enable/disable cache synchronization        |
| `aml.cache.sync.channel`           | String   | `cache-invalidation` | Redis Pub/Sub channel name                  |
| `aml.cache.sync.channel-prefix`    | String   | `""`                 | Prefix for channel (e.g., environment name) |
| `aml.cache.sync.compress-messages` | Boolean  | `false`              | Enable Gzip compression for messages        |
| `aml.cache.sync.caffeine-ttl`      | Duration | `5m`                 | Time-to-live for local Caffeine cache       |
| `aml.cache.sync.caffeine-max-size` | Long     | `1000`               | Maximum entries in local cache              |
| `aml.cache.sync.redis-ttl`         | Duration | `30m`                | Time-to-live for Redis cache                |

## Usage Examples

### Basic Caching

```java
@Service
public class ProductService {
    
    @Cacheable(value = "products", key = "#sku")
    public Product getProduct(String sku) {
        return productRepository.findBySku(sku);
    }
    
    @CachePut(value = "products", key = "#product.sku")
    public Product updateProduct(Product product) {
        return productRepository.save(product);
    }
    
    @CacheEvict(value = "products", key = "#sku")
    public void deleteProduct(String sku) {
        productRepository.deleteBySku(sku);
    }
}
```

### Batch Operations

```java
@Service
public class OrderService {
    
    @Caching(evict = {
        @CacheEvict(value = "orders", key = "#order.id"),
        @CacheEvict(value = "customerOrders", key = "#order.customerId")
    })
    public void cancelOrder(Order order) {
        orderRepository.cancel(order);
        // Multiple caches evicted and synced across instances
    }
}
```

### Complex SpEL Keys

```java
@Service
public class UserService {
    
    @Cacheable(value = "users", 
               key = "#root.methodName + ':' + #email.toLowerCase()")
    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    @CacheEvict(value = "users", 
                key = "'findByEmail:' + #user.email.toLowerCase()")
    public void updateUser(User user) {
        userRepository.save(user);
    }
}
```

### Conditional Caching

```java
@Service
public class ReportService {
    
    @Cacheable(value = "reports", 
               key = "#reportId",
               condition = "#reportId != null && #reportId.length() > 0",
               unless = "#result == null")
    public Report generateReport(String reportId) {
        return reportGenerator.generate(reportId);
    }
}
```

## Actuator Endpoint

### Enabling the Endpoint

```yaml
management:
  endpoints:
    web:
      exposure:
        include: cache-sync
```

### Available Operations

**GET `/actuator/cache-sync`**

View recent cache synchronization messages:

```json
{
  "enabled": true,
  "channelName": "prod:cache-invalidation",
  "compressMessages": true,
  "recentMessages": [
    {
      "timestamp": "2025-11-13T10:30:45.123Z",
      "cacheName": "countries",
      "action": "EVICT",
      "keys": ["KE", "UG", "TZ"]
    },
    {
      "timestamp": "2025-11-13T10:28:12.456Z",
      "cacheName": "products",
      "action": "CLEAR",
      "keys": null
    }
  ],
  "statistics": {
    "messagesPublished": 1247,
    "messagesReceived": 2485,
    "lastPublishedAt": "2025-11-13T10:30:45.123Z",
    "lastReceivedAt": "2025-11-13T10:30:45.456Z"
  }
}
```

**POST `/actuator/cache-sync/clear`**

Manually trigger cache clearing (requires admin privileges):

```bash
curl -X POST http://localhost:8080/actuator/cache-sync/clear \
  -H "Content-Type: application/json" \
  -d '{"cacheName": "products"}'
```

## Testing

### Unit Testing

```java
@Test
void cacheEvictionSyncsAcrossInstances() {
    CacheManager cm = new ConcurrentMapCacheManager("countries");
    CacheSyncProperties props = new CacheSyncProperties();
    props.setChannel("test-channel");
    
    RedisTemplate<String, String> template = mock(RedisTemplate.class);
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    
    CacheSyncService service = new CacheSyncService(
        template, cm, container, props
    );

    Cache cache = cm.getCache("countries");
    cache.put("KE", "Kenya");
    
    CacheMessage message = new CacheMessage(
        "countries", 
        List.of("KE"), 
        CacheAction.EVICT
    );
    
    service.onMessage(JsonUtils.toJson(message), null);
    
    assertNull(cache.get("KE"));
}
```

### Integration Testing with Testcontainers

```java
@SpringBootTest
@Testcontainers
class CacheSyncIntegrationTest {
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }
    
    @Autowired
    private CountryService countryService;
    
    @Autowired
    private CacheManager cacheManager;
    
    @Test
    void evictionPropagatesViaRedis() throws InterruptedException {
        // Cache country
        Country kenya = countryService.getCountryById("KE");
        assertNotNull(cacheManager.getCache("countries").get("KE"));
        
        // Evict cache
        countryService.updateCountry(kenya);
        
        // Wait for Pub/Sub propagation
        Thread.sleep(100);
        
        // Verify eviction
        assertNull(cacheManager.getCache("countries").get("KE"));
    }
}
```

## Advanced Configuration

### Custom Cache Manager

Override the default cache manager:

```java
@Configuration
public class CustomCacheConfig {
    
    @Bean
    @Primary
    public CacheManager customCacheManager(
            RedisConnectionFactory redisConnectionFactory) {
        
        // Custom Caffeine configuration
        CaffeineCacheManager caffeine = new CaffeineCacheManager();
        caffeine.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .recordStats());  // Enable metrics
        
        // Custom Redis configuration
        RedisCacheConfiguration redisCfg = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofHours(2))
            .disableCachingNullValues()
            .serializeValuesWith(RedisSerializationContext
                .SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        RedisCacheManager redis = RedisCacheManager
            .builder(redisConnectionFactory)
            .cacheDefaults(redisCfg)
            .build();
        
        return new CompositeCacheManager(caffeine, redis);
    }
}
```

### Security Configuration

```yaml
spring:
  data:
    redis:
      ssl: true
      password: ${REDIS_PASSWORD}
      username: ${REDIS_USERNAME}
      
aml:
  cache:
    sync:
      channel-prefix: "${APP_ENV}:${APP_NAME}:"
      # Results in: "prod:order-service:cache-invalidation"
```

### Metrics Integration

```java
@Component
@SuppressWarnings("unused")
public class CacheMetricsCollector {
    
    private final MeterRegistry registry;
    
    @EventListener
    public void onCacheEvict(CacheEvictEvent event) {
        registry.counter("cache.evictions",
            "cache", event.getCacheName(),
            "action", event.getAction().name()
        ).increment();
    }
}
```

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Setup

```bash
# Clone the repository
git clone https://github.com/scytalesystems/cache-sync-starter.git

# Build the project
mvn clean install

# Run tests
mvn test

# Run integration tests (requires Docker)
mvn verify -P integration-tests
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Spring Boot Team for the excellent framework
- Caffeine Cache for high-performance local caching
- Redis for reliable distributed messaging
- Testcontainers for integration testing capabilities

---

For issues, questions, or feature requests, please [open an issue](https://github.com/scytalesystems/cache-sync-starter/issues).

**13 Nov 2025**
