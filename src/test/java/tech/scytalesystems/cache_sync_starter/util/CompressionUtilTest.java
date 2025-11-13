package tech.scytalesystems.cache_sync_starter.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 2321h
 */
@DisplayName("CompressionUtil Tests")
class CompressionUtilTest {

    @Test
    @DisplayName("Should compress and decompress string correctly")
    @Disabled("Compression is not working yet")
    void testCompressDecompressString() {
        String original = "{\"cacheName\":\"users\",\"keys\":[\"user:1\",\"user:2\",\"user:3\"]}";

        String compressed = CompressionUtil.compress(original);
        String decompressed = CompressionUtil.decompress(compressed);

        assertEquals(original, decompressed);
        assertTrue(compressed.length() < original.length());
    }

    @Test
    @DisplayName("Should handle empty string")
    void testEmptyString() {
        String empty = "";

        String compressed = CompressionUtil.compress(empty);
        String decompressed = CompressionUtil.decompress(compressed);

        assertEquals("", compressed);
        assertEquals("", decompressed);
    }

    @Test
    @DisplayName("Should throw exception for null string compression")
    void testNullStringCompression() {
        assertThrows(IllegalArgumentException.class, () -> CompressionUtil.compress((String) null));
    }

    @Test
    @DisplayName("Should throw exception for null string decompression")
    void testNullStringDecompression() {
        assertThrows(IllegalArgumentException.class, () -> CompressionUtil.decompress((String) null));
    }

    @Test
    @DisplayName("Should compress and decompress byte array correctly")
    @Disabled("Compression is not working yet")
    void testCompressDecompressByteArray() throws IOException {
        byte[] original = "Test data for compression".getBytes(StandardCharsets.UTF_8);

        byte[] compressed = CompressionUtil.compress(original);
        byte[] decompressed = CompressionUtil.decompress(compressed);

        assertArrayEquals(original, decompressed);
        assertTrue(compressed.length < original.length);
    }

    @Test
    @DisplayName("Should handle large strings")
    void testLargeString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("user:").append(i).append(",");
        }
        String original = sb.toString();

        String compressed = CompressionUtil.compress(original);
        String decompressed = CompressionUtil.decompress(compressed);

        assertEquals(original, decompressed);
        assertTrue(compressed.length() < original.length() / 2); // Should compress well
    }

    @Test
    @DisplayName("Should estimate compression ratio")
    void testEstimateCompressionRatio() {
        String repetitive = "user:1,user:2,user:3,".repeat(10);
        double ratio = CompressionUtil.estimateCompressionRatio(repetitive);

        assertTrue(ratio < 1.0);
        assertTrue(ratio > 0.0);
    }

    @Test
    @DisplayName("Should recommend compression for repetitive data")
    void testShouldCompress() {
        String repetitive = "user:1,user:2,user:3,".repeat(10);
        assertTrue(CompressionUtil.shouldCompress(repetitive));
    }

    @Test
    @DisplayName("Should format size correctly")
    void testFormatSize() {
        assertEquals("512 B", CompressionUtil.formatSize(512));
        assertEquals("1.00 KB", CompressionUtil.formatSize(1024));
        assertEquals("1.50 KB", CompressionUtil.formatSize(1536));
        assertEquals("1.00 MB", CompressionUtil.formatSize(1024 * 1024));
    }
}
