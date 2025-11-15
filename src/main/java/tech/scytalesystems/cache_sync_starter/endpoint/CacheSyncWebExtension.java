package tech.scytalesystems.cache_sync_starter.endpoint;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author Gathariki Ngigi
 * Created on 15/11/2025
 * Time 1847h
 */
@Component
@EndpointWebExtension(endpoint = CacheSyncEndpoint.class)
@SuppressWarnings("unused")
public class CacheSyncWebExtension {
    private final CacheSyncEndpoint delegate;

    public CacheSyncWebExtension(CacheSyncEndpoint delegate) {
        this.delegate = delegate;
    }

    @ReadOperation
    public Map<String,Object> caches() {
        return delegate.caches();
    }

    @WriteOperation
    public Map<String,Object> evict(String cacheName, List<String> keys) {
        return delegate.evict(cacheName, keys);
    }

    @WriteOperation
    public Map<String,Object> clear(String cacheName) {
        return delegate.clear(cacheName);
    }

    @WriteOperation
    public Map<String,Object> clearAll() {
        return delegate.clearAll();
    }
}