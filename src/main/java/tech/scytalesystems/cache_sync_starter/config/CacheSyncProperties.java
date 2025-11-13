package tech.scytalesystems.cache_sync_starter.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1313h
 * <p>
 * Configuration properties for the cache synchronization starter.
 * These properties are validated on application startup.
 */
@Validated
@ConfigurationProperties("app.cache.sync")
@SuppressWarnings("unused")
public class CacheSyncProperties {

    /**
     * A prefix for the Redis channel name to isolate environments (e.g., "prod:", "staging:").
     */
    private String channelPrefix = "";

    /**
     * The base name of the Redis Pub/Sub channel for cache invalidation messages.
     */
    @NotBlank(message = "Channel name cannot be blank")
    private String channel = "cache-invalidation";

    /**
     * Whether to enable the cache synchronization feature.
     */
    private boolean enabled = true;

    /**
     * Whether to compress messages before sending them to Redis.
     * Recommended for large payloads or to save network bandwidth.
     */
    private boolean compressMessages = false;

    /**
     * The Time-To-Live (TTL) for entries in the L1 Caffeine cache.
     */
    @NotNull(message = "Caffeine TTL cannot be null")
    private Duration caffeineTtl = Duration.ofMinutes(5);

    /**
     * The maximum number of entries in the L1 Caffeine cache.
     */
    @Min(value = 1, message = "Caffeine max size must be at least 1")
    private long caffeineMaxSize = 1000L;

    /**
     * The Time-To-Live (TTL) for entries in the L2 Redis cache.
     */
    @NotNull(message = "Redis TTL cannot be null")
    private Duration redisTtl = Duration.ofMinutes(30);

    // GETTERS
    public String getChannelPrefix() {
        return this.channelPrefix;
    }

    public String getChannel() {
        return this.channel;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean isCompressMessages() {
        return this.compressMessages;
    }

    public Duration getCaffeineTtl() {
        return this.caffeineTtl;
    }

    public long getCaffeineMaxSize() {
        return this.caffeineMaxSize;
    }

    public Duration getRedisTtl() {
        return this.redisTtl;
    }

    // SETTERS
    public void setChannelPrefix(String channelPrefix) {
        this.channelPrefix = channelPrefix;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setCompressMessages(boolean compressMessages) {
        this.compressMessages = compressMessages;
    }

    public void setCaffeineTtl(Duration caffeineTtl) {
        this.caffeineTtl = caffeineTtl;
    }

    public void setCaffeineMaxSize(long caffeineMaxSize) {
        this.caffeineMaxSize = caffeineMaxSize;
    }

    public void setRedisTtl(Duration redisTtl) {
        this.redisTtl = redisTtl;
    }
}
