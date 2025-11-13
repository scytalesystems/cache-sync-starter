package tech.scytalesystems.cache_sync_starter.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 2326h
 */
@DisplayName("CacheMessage Tests")
class CacheMessageTest {

    @Test
    @DisplayName("Should build message with all fields")
    void testBuilder() {
        CacheMessage message = CacheMessage.builder()
                .cacheName("users")
                .keys("user:1", "user:2")
                .action(CacheAction.EVICT)
                .instanceId("test-123")
                .build();

        assertEquals("users", message.getCacheName());
        assertEquals(2, message.getKeys().size());
        assertEquals(CacheAction.EVICT, message.getAction());
        assertEquals("test-123", message.getInstanceId());
    }

    @Test
    @DisplayName("Should add keys incrementally")
    void testAddKey() {
        CacheMessage message = CacheMessage.builder()
                .cacheName("orders")
                .addKey("order:1")
                .addKey("order:2")
                .addKey("order:3")
                .action(CacheAction.EVICT)
                .build();

        assertEquals(3, message.getKeys().size());
        assertTrue(message.getKeys().contains("order:1"));
        assertTrue(message.getKeys().contains("order:2"));
        assertTrue(message.getKeys().contains("order:3"));
    }

    @Test
    @DisplayName("Should accept keys as list")
    void testKeysAsList() {
        List<String> keys = Arrays.asList("key1", "key2", "key3");

        CacheMessage message = CacheMessage.builder()
                .cacheName("test")
                .keys(keys)
                .action(CacheAction.EVICT)
                .build();

        assertEquals(3, message.getKeys().size());
        assertEquals(keys, message.getKeys());
    }

    @Test
    @DisplayName("Should handle empty keys for CLEAR action")
    void testClearAction() {
        CacheMessage message = CacheMessage.builder()
                .cacheName("cache")
                .action(CacheAction.CLEAR)
                .build();

        assertEquals(CacheAction.CLEAR, message.getAction());
        assertNotNull(message.getKeys());
        assertTrue(message.getKeys().isEmpty());
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void testEquals() {
        CacheMessage msg1 = CacheMessage.builder()
                .cacheName("users")
                .keys("user:1")
                .action(CacheAction.EVICT)
                .instanceId("123")
                .build();

        CacheMessage msg2 = CacheMessage.builder()
                .cacheName("users")
                .keys("user:1")
                .action(CacheAction.EVICT)
                .instanceId("123")
                .build();

        assertEquals(msg1, msg2);
        assertEquals(msg1.hashCode(), msg2.hashCode());
    }

    @Test
    @DisplayName("Should have meaningful toString")
    void testToString() {
        CacheMessage message = CacheMessage.builder()
                .cacheName("products")
                .keys("p:1", "p:2")
                .action(CacheAction.EVICT)
                .instanceId("instance-123456789")
                .build();

        String str = message.toString();

        assertTrue(str.contains("products"));
        assertTrue(str.contains("EVICT"));
        assertTrue(str.contains("instance")); // instanceId truncated
    }
}
