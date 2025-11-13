package tech.scytalesystems.cache_sync_starter.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1303h
 * <p>Cache invalidation message sent via Redis Pub/Sub.
 * <p>Contains information about which cache and keys to evict or clear.
 */
@SuppressWarnings("unused")
public class CacheMessage {
    private String cacheName;
    private List<String> keys;
    private CacheAction action;
    private String instanceId;

    // CONSTRUCTORS
    public CacheMessage() {
        this.keys = new ArrayList<>();
    }

    public CacheMessage(String cacheName, List<String> keys, CacheAction action, String instanceId) {
        this.cacheName = cacheName;
        this.keys = keys != null ? keys : new ArrayList<>();
        this.action = action;
        this.instanceId = instanceId;
    }

    // ========== BUILDER ==========

    public static CacheMessageBuilder builder() {
        return new CacheMessageBuilder();
    }

    public static class CacheMessageBuilder {
        private String cacheName;
        private List<String> keys;
        private CacheAction action;
        private String instanceId;

        CacheMessageBuilder() {
            this.keys = new ArrayList<>();
        }

        public CacheMessageBuilder cacheName(String cacheName) {
            this.cacheName = cacheName;
            return this;
        }

        @SuppressWarnings("all")
        public CacheMessageBuilder keys(List<String> keys) {
            this.keys = keys != null ? new ArrayList<>(keys) : new ArrayList<>();
            return this;
        }

        public CacheMessageBuilder keys(String... keys) {
            this.keys = keys != null ? Arrays.asList(keys) : new ArrayList<>();
            return this;
        }

        public CacheMessageBuilder addKey(String key) {
            if (this.keys == null) this.keys = new ArrayList<>();

            this.keys.add(key);
            return this;
        }

        public CacheMessageBuilder action(CacheAction action) {
            this.action = action;
            return this;
        }

        public CacheMessageBuilder instanceId(String instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public CacheMessage build() {
            return new CacheMessage(cacheName, keys, action, instanceId);
        }

        @Override
        public String toString() {
            return "CacheMessage.CacheMessageBuilder(cacheName=" + cacheName +
                    ", keys=" + keys +
                    ", action=" + action +
                    ", instanceId=" + instanceId + ")";
        }
    }

    // GETTERS
    public String getCacheName() {
        return this.cacheName;
    }

    public List<String> getKeys() {
        return this.keys;
    }

    public CacheAction getAction() {
        return this.action;
    }

    public String getInstanceId() {
        return this.instanceId;
    }

    // SETTERS
    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    public void setAction(CacheAction action) {
        this.action = action;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public String toString() {
        return "CacheMessage{" +
                "cacheName='" + cacheName + '\'' +
                ", keys=" + keys +
                ", action=" + action +
                ", instanceId='" + (instanceId != null ? instanceId.substring(0, Math.min(8, instanceId.length())) + "..." : "null") + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CacheMessage that = (CacheMessage) o;

        if (!Objects.equals(cacheName, that.cacheName)) return false;
        if (!Objects.equals(keys, that.keys)) return false;
        if (action != that.action) return false;

        return Objects.equals(instanceId, that.instanceId);
    }

    @Override
    public int hashCode() {
        int result = cacheName != null ? cacheName.hashCode() : 0;

        result = 31 * result + (keys != null ? keys.hashCode() : 0);
        result = 31 * result + (action != null ? action.hashCode() : 0);
        result = 31 * result + (instanceId != null ? instanceId.hashCode() : 0);

        return result;
    }
}
