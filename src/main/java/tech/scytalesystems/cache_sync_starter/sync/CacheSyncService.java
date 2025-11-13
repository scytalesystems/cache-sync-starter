package tech.scytalesystems.cache_sync_starter.sync;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import tech.scytalesystems.cache_sync_starter.config.CacheSyncProperties;
import tech.scytalesystems.cache_sync_starter.dto.CacheAction;
import tech.scytalesystems.cache_sync_starter.dto.CacheMessage;
import tech.scytalesystems.cache_sync_starter.util.GzipUtils;
import tech.scytalesystems.cache_sync_starter.util.JsonUtils;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1312h
 * <p>Service for cache synchronization - listens and broadcasts automatically
 */
public class CacheSyncService {
    private final RedisTemplate<String, String> redisTemplate;
    private final CacheManager cacheManager;
    private final CacheSyncProperties props;
    private final String channelName;

    public CacheSyncService(RedisTemplate<String, String> redisTemplate,
                            CacheManager cacheManager,
                            RedisMessageListenerContainer container,
                            CacheSyncProperties props) {
        this.redisTemplate = redisTemplate;
        this.cacheManager = cacheManager;
        this.props = props;
        this.channelName = props.getChannelPrefix() + props.getChannel();

        MessageListenerAdapter adapter = new MessageListenerAdapter(this, "onMessage");
        container.addMessageListener(adapter, new ChannelTopic(channelName));
    }

    public void publish(CacheMessage msg) {
        String json = JsonUtils.toJson(msg);

        if (props.isCompressMessages()) json = GzipUtils.compress(json);

        redisTemplate.convertAndSend(channelName, json);
    }

    @SuppressWarnings("unused")
    public void onMessage(String message, String pattern) {
        String payload = props.isCompressMessages() ? GzipUtils.decompress(message) : message;
        CacheMessage msg = JsonUtils.fromJson(payload, CacheMessage.class);
        Cache cache = cacheManager.getCache(msg.cacheName());

        if (cache == null) return;

        if (msg.action() == CacheAction.CLEAR) cache.clear();
        else if (msg.keys() != null) msg.keys().forEach(cache::evict);
    }
}
