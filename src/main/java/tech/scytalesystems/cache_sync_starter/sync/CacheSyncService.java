package tech.scytalesystems.cache_sync_starter.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import tech.scytalesystems.cache_sync_starter.cache.TwoTierCache;
import tech.scytalesystems.cache_sync_starter.config.CacheSyncProperties;
import tech.scytalesystems.cache_sync_starter.dto.CacheAction;
import tech.scytalesystems.cache_sync_starter.dto.CacheMessage;
import tech.scytalesystems.cache_sync_starter.metrics.CacheSyncMetrics;
import tech.scytalesystems.cache_sync_starter.util.CompressionUtil;
import tech.scytalesystems.cache_sync_starter.util.JsonUtil;

import java.util.UUID;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1312h
 * Publishes and receives cache invalidation messages via Redis Pub/Sub.
 *
 * Architecture:
 * - Each application instance has a unique UUID (instanceId)
 * - When @CacheEvict is triggered locally, publishes message to Redis channel
 * - All other instances receive the message and evict from their L1 (Caffeine) cache
 * - Redis (L2) cache is the source of truth and not evicted on remote messages
 *
 * Loop Prevention:
 * 1. Instance ID: Messages from self are ignored (same UUID)
 * 2. ThreadLocal Flag: Prevents re-publishing during remote eviction processing
 *
 * Message Flow Example:
 * Instance A: @CacheEvict → evict L1+L2 → publish message
 * Instance B: receive message → check instanceId → evict L1 only (not L2)
 * Instance C: receive message → check instanceId → evict L1 only (not L2)
 *
 * Why L1-only eviction on remote?
 * - Redis (L2) is already updated by the originating instance
 * - Other instances only need to clear their local Caffeine cache
 * - This ensures consistency without redundant Redis operations
 */
@SuppressWarnings("all")
public class CacheSyncService {
    private static final Logger log = LoggerFactory.getLogger(CacheSyncService.class);

    /**
     * ThreadLocal flag indicating whether we're currently processing a remote eviction.
     * Used to prevent infinite loops where receiving a message triggers another publish.
     *
     * Flow without flag:
     * 1. Receive remote message
     * 2. Call cache.evict()
     * 3. CacheEvictAspect intercepts
     * 4. Publishes another message
     * 5. Infinite loop!
     *
     * Flow with flag:
     * 1. Receive remote message
     * 2. Set REMOTE_EVICTION = true
     * 3. Call cache.evict()
     * 4. CacheEvictAspect checks flag and skips publish
     * 5. Clear flag
     * 6. No infinite loop!
     */
    private static final ThreadLocal<Boolean> REMOTE_EVICTION = ThreadLocal.withInitial(() -> false);

    private final RedisTemplate<String, String> redisTemplate;
    private final CacheManager cacheManager;
    private final CacheSyncProperties props;
    private final String channelName;
    private final String instanceId;

    /**
     * Optional metrics bean for monitoring cache sync operations.
     * If a CacheSyncMetrics bean exists in the application context, it will be autowired.
     * If not, this will be null and metrics will be skipped.
     */
    @Autowired(required = false)
    private CacheSyncMetrics metrics;

    /**
     * Constructor that initializes the cache sync service and subscribes to Redis channel.
     *
     * @param redisTemplate Redis template for publishing messages
     * @param cacheManager Cache manager (typically TwoTierCacheManager)
     * @param container Redis message listener container
     * @param props Configuration properties for cache sync
     */
    public CacheSyncService(
            RedisTemplate<String, String> redisTemplate,
            CacheManager cacheManager,
            RedisMessageListenerContainer container,
            CacheSyncProperties props) {

        this.redisTemplate = redisTemplate;
        this.cacheManager = cacheManager;
        this.props = props;
        this.instanceId = UUID.randomUUID().toString();
        this.channelName = buildChannelName(props);

        // Subscribe to Redis pub/sub channel
        // MessageListenerAdapter will invoke the "onMessage" method when messages arrive
        MessageListenerAdapter adapter = new MessageListenerAdapter(this, "onMessage");
        container.addMessageListener(adapter, new ChannelTopic(channelName));

        log.info("CacheSyncService initialized - instanceId: {}, channel: {}, compression: {}",
                instanceId, channelName, props.isCompressMessages());
    }

    /**
     * Builds the full Redis channel name by combining prefix and channel name.
     *
     * Examples:
     * - channelPrefix="prod:", channel="cache-invalidation" → "prod:cache-invalidation"
     * - channelPrefix="", channel="cache-invalidation" → "cache-invalidation"
     * - channelPrefix="staging:", channel="cache-sync" → "staging:cache-sync"
     *
     * This allows environment isolation (prod/staging/dev don't affect each other).
     *
     * @param props Configuration properties
     * @return Full channel name with prefix
     */
    private String buildChannelName(CacheSyncProperties props) {
        String prefix = props.getChannelPrefix();
        String channel = props.getChannel();

        if (prefix != null && !prefix.isBlank()) return prefix + channel;

        return channel;
    }

    /**
     * Publishes a cache invalidation message to Redis pub/sub channel.
     *
     * This method is called by:
     * 1. CacheEvictAspect when @CacheEvict is triggered
     * 2. Manual calls from application code
     *
     * Important behaviors:
     * - Does NOT publish if currently processing a remote eviction (prevents infinite loops)
     * - Adds instance ID to message for self-identification
     * - Optionally compresses message if enabled in properties
     * - Logs warnings on failure but doesn't throw exceptions (graceful degradation)
     * - Records metrics if CacheSyncMetrics bean is available
     *
     * @param msg Cache message containing cache name, keys, and action (EVICT or CLEAR)
     */
    public void publish(CacheMessage msg) {
        // CRITICAL: Don't re-publish during remote eviction processing
        // This prevents infinite loops
        if (REMOTE_EVICTION.get()) {
            log.trace("Skipping publish during remote eviction: cacheName={}", msg.getCacheName());
            return;
        }

        try {
            // Add instance ID so other instances can identify messages from self
            msg.setInstanceId(instanceId);

            // Serialize message to JSON
            String json = JsonUtil.toJson(msg);

            // Compress if enabled (useful for messages with many keys)
            // Trade-off: Reduces network bandwidth but increases CPU usage
            if (props.isCompressMessages()) json = CompressionUtil.compress(json);

            // Publish to Redis channel
            // All instances subscribed to this channel will receive the message
            redisTemplate.convertAndSend(channelName, json);

            log.debug("Published cache message: cacheName={}, action={}, keyCount={}, compressed={}",
                    msg.getCacheName(), msg.getAction(), msg.getKeys().size(), props.isCompressMessages());

            // Record metrics if available
            if (metrics != null) metrics.recordPublish(msg.getCacheName(), msg.getAction(), msg.getKeys().size());
        } catch (Exception e) {
            // Log warning but don't fail the main operation
            // Cache sync failure shouldn't break the application flow
            log.warn("Failed to publish cache sync message for cache: {}, error: {}",
                    msg.getCacheName(), e.getMessage(), e);

            if (metrics != null) metrics.recordError(msg.getCacheName(), e);
        }
    }

    /**
     * Receives and processes cache invalidation messages from Redis pub/sub.
     *
     * This method is invoked by MessageListenerAdapter when a message arrives on the channel.
     *
     * Message Processing Flow:
     * 1. Decompress message if compression is enabled
     * 2. Deserialize JSON to CacheMessage object
     * 3. Check if message is from self (same instanceId) → ignore if yes
     * 4. Get the cache from CacheManager
     * 5. Set REMOTE_EVICTION flag to prevent re-publishing
     * 6. Evict keys from L1 (Caffeine) cache only
     * 7. Clear REMOTE_EVICTION flag in finally block
     * 8. Record metrics if available
     *
     * Important behaviors:
     * - Ignores messages from self (same instanceId)
     * - Only evicts from L1 (Caffeine) cache, not L2 (Redis)
     * - Sets REMOTE_EVICTION flag to prevent CacheEvictAspect from re-publishing
     * - Properly cleans up ThreadLocal in finally block (prevents memory leaks)
     * - Catches all exceptions to prevent message processing from crashing
     *
     * @param message The Redis pub/sub message payload (JSON string, possibly compressed)
     * @param pattern The channel pattern (not used but required by MessageListenerAdapter signature)
     */
    @SuppressWarnings("unused")
    public void onMessage(String message, String pattern) {
        CacheMessage msg = null;

        try {
            // Decompress if compression is enabled
            String payload = props.isCompressMessages() ? CompressionUtil.decompress(message) : message;

            // Deserialize JSON to CacheMessage object
            msg = JsonUtil.fromJson(payload, CacheMessage.class);

            // CRITICAL: Ignore messages from self
            // Without this check, we would process our own evictions unnecessarily
            if (instanceId.equals(msg.getInstanceId())) {
                log.trace("Ignoring self-published message: cacheName={}, instanceId={}",
                        msg.getCacheName(), instanceId);

                if (metrics != null) metrics.recordSelfMessageIgnored();

                return;
            }

            // Get the cache from CacheManager
            Cache cache = cacheManager.getCache(msg.getCacheName());
            if (cache == null) {
                log.warn("Received message for unknown cache: {}, available caches: {}",
                        msg.getCacheName(), cacheManager.getCacheNames());
                return;
            }

            // CRITICAL: Set flag to prevent re-publishing during eviction
            // This prevents infinite loops
            REMOTE_EVICTION.set(true);
            try {
                // Process the cache message (evict or clear)
                processMessage(cache, msg);

                log.debug("Processed remote cache message: cacheName={}, action={}, keyCount={}, fromInstance={}",
                        msg.getCacheName(), msg.getAction(), msg.getKeys().size(),
                        msg.getInstanceId().substring(0, 8) + "...");

                // Record metrics if available
                if (metrics != null) metrics.recordReceive(msg.getCacheName(), msg.getAction(), msg.getKeys().size());
            } finally {
                // CRITICAL: Always clean up ThreadLocal to prevent memory leaks
                // ThreadLocals must be removed when no longer needed
                REMOTE_EVICTION.remove();
            }
        } catch (Exception e) {
            // Log error but don't throw - message processing failures shouldn't crash the listener
            String cacheName = (msg != null) ? msg.getCacheName() : "unknown";
            log.error("Error processing cache sync message for cache: {}, error: {}",
                    cacheName, e.getMessage(), e);

            if (metrics != null) metrics.recordError(cacheName, e);
        }
    }

    /**
     * Processes the cache message by evicting specific keys or clearing the entire cache.
     *
     * Key Decision: L1-Only Eviction
     * --------------------------------
     * When receiving a remote message, we only evict from L1 (Caffeine), not L2 (Redis).
     *
     * Why?
     * - The originating instance already updated L2 (Redis) when it evicted locally
     * - Redis is the source of truth and already has the correct state
     * - Other instances only need to clear their local Caffeine cache
     * - This prevents redundant Redis operations and potential race conditions
     *
     * Example Flow:
     * Instance A: evict("user:123") → clears L1+L2 → publishes message
     * Instance B: receives message → clears L1 only (L2 already correct)
     * Instance C: receives message → clears L1 only (L2 already correct)
     *
     * @param cache The cache to process (may be TwoTierCache or single-tier)
     * @param msg The cache message containing action and keys
     */
    private void processMessage(Cache cache, CacheMessage msg) {
        // Determine target cache: L1 (Caffeine) only
        Cache targetCache = cache;

        // If using TwoTierCache, extract the L1 cache
        if (cache instanceof TwoTierCache) {
            TwoTierCache twoTierCache = (TwoTierCache) cache;
            targetCache = twoTierCache.l1Cache();

            log.trace("Using L1 cache for remote eviction: cacheName={}", msg.getCacheName());
        }

        // Validate target cache exists
        if (targetCache == null) {
            log.warn("No L1 cache available for: {}", msg.getCacheName());
            return;
        }

        // Process the action
        switch (msg.getAction()) {
            case CacheAction.EVICT -> {
                // Evict specific keys from L1 cache
                int evictedCount = 0;
                for (String key : msg.getKeys()) {
                    targetCache.evict(key);
                    evictedCount++;
                }
                log.debug("Evicted {} keys from L1 cache: {}", evictedCount, msg.getCacheName());
            }

            case CacheAction.CLEAR -> {
                // Clear entire L1 cache
                targetCache.clear();
                log.debug("Cleared L1 cache: {}", msg.getCacheName());
            }

            default -> {
                log.warn("Unknown cache action: {}, cacheName: {}", msg.getAction(), msg.getCacheName());
            }
        }
    }

    /**
     * Returns the unique instance ID for this service instance.
     * This UUID is used to identify self-published messages.
     *
     * @return UUID instance identifier
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Returns the full Redis channel name being used (includes prefix if configured).
     *
     * @return Redis pub/sub channel name
     */
    public String getChannelName() {
        return channelName;
    }

    /**
     * Checks if currently processing a remote eviction.
     * This is used by CacheEvictAspect to prevent re-publishing.
     *
     * @return true if processing remote eviction, false otherwise
     */
    public static boolean isRemoteEviction() {
        return REMOTE_EVICTION.get();
    }

    /**
     * Sets the metrics implementation (primarily for testing or manual wiring).
     *
     * @param metrics Metrics implementation
     */
    public void setMetrics(CacheSyncMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Returns the configured cache sync properties.
     * Primarily for testing and diagnostics.
     *
     * @return Configuration properties
     */
    public CacheSyncProperties getProperties() {
        return props;
    }

    /**
     * Returns the CacheManager being used.
     * Primarily for testing and diagnostics.
     *
     * @return Cache manager instance
     */
    public CacheManager getCacheManager() {
        return cacheManager;
    }
}