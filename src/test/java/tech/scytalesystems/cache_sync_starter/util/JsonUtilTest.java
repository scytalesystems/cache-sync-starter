package tech.scytalesystems.cache_sync_starter.util;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.scytalesystems.cache_sync_starter.dto.CacheAction;
import tech.scytalesystems.cache_sync_starter.dto.CacheMessage;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 2325h
 */
@DisplayName("JsonUtil Tests")
class JsonUtilTest {

    @Test
    @DisplayName("Should serialize object to JSON")
    void testToJson() {
        CacheMessage message = CacheMessage.builder()
                .cacheName("users")
                .keys("user:1", "user:2")
                .action(CacheAction.EVICT)
                .instanceId("test-123")
                .build();

        String json = JsonUtil.toJson(message);

        assertNotNull(json);
        assertTrue(json.contains("users"));
        assertTrue(json.contains("user:1"));
    }

    @Test
    @DisplayName("Should deserialize JSON to object")
    void testFromJson() {
        String json = "{\"cacheName\":\"users\",\"keys\":[\"user:1\"],\"action\":\"EVICT\",\"instanceId\":\"test-123\"}";

        CacheMessage message = JsonUtil.fromJson(json, CacheMessage.class);

        assertNotNull(message);
        assertEquals("users", message.getCacheName());
        assertEquals(1, message.getKeys().size());
        assertEquals("user:1", message.getKeys().getFirst());
        assertEquals(CacheAction.EVICT, message.getAction());
    }

    @Test
    @DisplayName("Should handle null fields gracefully")
    void testNullFields() {
        CacheMessage message = new CacheMessage();

        String json = JsonUtil.toJson(message);
        CacheMessage deserialized = JsonUtil.fromJson(json, CacheMessage.class);

        assertNotNull(deserialized);
    }

    @Test
    @DisplayName("Should round-trip serialize and deserialize")
    void testRoundTrip() {
        CacheMessage original = CacheMessage.builder()
                .cacheName("products")
                .keys(Arrays.asList("p:1", "p:2", "p:3"))
                .action(CacheAction.CLEAR)
                .instanceId("instance-456")
                .build();

        String json = JsonUtil.toJson(original);
        CacheMessage deserialized = JsonUtil.fromJson(json, CacheMessage.class);

        assertNotNull(deserialized);
        assertEquals(original.getCacheName(), deserialized.getCacheName());
        assertEquals(original.getKeys(), deserialized.getKeys());
        assertEquals(original.getAction(), deserialized.getAction());
        assertEquals(original.getInstanceId(), deserialized.getInstanceId());
    }
}