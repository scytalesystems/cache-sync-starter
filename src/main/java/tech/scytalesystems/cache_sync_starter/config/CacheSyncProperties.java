package tech.scytalesystems.cache_sync_starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1313h
 */
@Data
@ConfigurationProperties("aml.cache.sync")
public class CacheSyncProperties {
    private String channelPrefix = ""; // environment prefix, e.g. "prod:"
    private String channel = "cache-invalidation";
    private boolean enabled = true;
    private boolean compressMessages = false;
    private Duration caffeineTtl = Duration.ofMinutes(5);
    private long caffeineMaxSize = 1000L;
    private Duration redisTtl = Duration.ofMinutes(30);
}
