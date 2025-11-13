package tech.scytalesystems.cache_sync_starter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 2342h
 */
@DisplayName("CacheSyncProperties Tests")
class CacheSyncPropertiesTest {

    @Test
    @DisplayName("Should have correct default values")
    void testDefaults() {
        CacheSyncProperties props = new CacheSyncProperties();

        assertEquals("", props.getChannelPrefix());
        assertEquals("cache-invalidation", props.getChannel());
        assertTrue(props.isEnabled());
        assertFalse(props.isCompressMessages());
        assertEquals(Duration.ofMinutes(5), props.getCaffeineTtl());
        assertEquals(1000L, props.getCaffeineMaxSize());
        assertEquals(Duration.ofMinutes(30), props.getRedisTtl());
    }

    @Test
    @DisplayName("Should allow setting all properties")
    void testSetters() {
        CacheSyncProperties props = new CacheSyncProperties();

        props.setChannelPrefix("prod:");
        props.setChannel("custom-channel");
        props.setEnabled(false);
        props.setCompressMessages(true);
        props.setCaffeineTtl(Duration.ofMinutes(10));
        props.setCaffeineMaxSize(5000L);
        props.setRedisTtl(Duration.ofHours(1));

        assertEquals("prod:", props.getChannelPrefix());
        assertEquals("custom-channel", props.getChannel());
        assertFalse(props.isEnabled());
        assertTrue(props.isCompressMessages());
        assertEquals(Duration.ofMinutes(10), props.getCaffeineTtl());
        assertEquals(5000L, props.getCaffeineMaxSize());
        assertEquals(Duration.ofHours(1), props.getRedisTtl());
    }
}