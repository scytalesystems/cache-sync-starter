package tech.scytalesystems.cache_sync_starter.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1310h
 * <p>
 * GZIP compression utility for cache messages.
 * <p>
 * Purpose:
 * - Reduces network bandwidth when publishing cache invalidation messages
 * - Particularly useful when evicting many keys at once
 * - Messages are compressed with GZIP and encoded to Base64 for Redis transport
 * <p>
 * Trade-offs:
 * - Pros: Reduces network bandwidth (40-70% typical compression)
 * - Cons: Increases CPU usage (compression/decompression overhead)
 * <p>
 * When to Enable:
 * - Large key lists (> 100 keys per message)
 * - Limited network bandwidth
 * - High message frequency
 * <p>
 * When to Disable:
 * - Small key lists (< 20 keys per message)
 * - CPU-constrained environments
 * - Low message frequency
 *
 * Thread Safety: All methods are stateless and thread-safe
 */
@SuppressWarnings("all")
public class CompressionUtil {
    private static final Logger log = LoggerFactory.getLogger(CompressionUtil.class);

    // Buffer size for streaming operations
    private static final int BUFFER_SIZE = 4096;

    /**
     * Compresses a string using GZIP and encodes it to Base64.
     *
     * Flow:
     * 1. Convert string to UTF-8 bytes
     * 2. Compress bytes with GZIP
     * 3. Encode compressed bytes to Base64 string
     *
     * Example:
     * Input:  "{"cacheName":"users","keys":["user:1","user:2"]}"  (52 bytes)
     * Output: "H4sIAAAAAAAA/6tWKkktLlGyUlAqS8..."                 (38 bytes)
     *
     * @param str the string to compress (typically JSON)
     * @return Base64-encoded GZIP compressed string
     * @throws UncheckedIOException if compression fails
     * @throws IllegalArgumentException if input is null
     */
    public static String compress(String str) {
        if (str == null) throw new IllegalArgumentException("Input string cannot be null");

        if (str.isEmpty()) return "";

        try (ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutput = new GZIPOutputStream(byteOutput)) {

            // Write UTF-8 bytes to GZIP stream
            byte[] inputBytes = str.getBytes(StandardCharsets.UTF_8);
            gzipOutput.write(inputBytes);

            // CRITICAL: Must call finish() to complete GZIP format
            gzipOutput.finish();

            // Encode compressed bytes to Base64
            byte[] compressedBytes = byteOutput.toByteArray();
            String result = Base64.getEncoder().encodeToString(compressedBytes);

            if (log.isTraceEnabled()) {
                log.trace("Compressed string: {} bytes → {} bytes ({}% reduction)",
                        inputBytes.length, compressedBytes.length, 100 - (compressedBytes.length * 100 / inputBytes.length));
            }

            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compress string (length: " + str.length() + ")", e);
        }
    }

    /**
     * Decompresses a Base64-encoded GZIP string.
     *
     * Flow:
     * 1. Decode Base64 string to bytes
     * 2. Decompress bytes with GZIP
     * 3. Convert decompressed bytes to UTF-8 string
     *
     * Example:
     * Input:  "H4sIAAAAAAAA/6tWKkktLlGyUlAqS8..."                 (38 bytes)
     * Output: "{"cacheName":"users","keys":["user:1","user:2"]}"  (52 bytes)
     *
     * @param compressed Base64-encoded GZIP compressed string
     * @return decompressed original string
     * @throws UncheckedIOException if decompression fails
     * @throws IllegalArgumentException if input is null or invalid
     */
    public static String decompress(String compressed) {
        if (compressed == null) throw new IllegalArgumentException("Input string cannot be null");

        if (compressed.isEmpty()) return "";

        try {
            // Decode Base64 to bytes
            byte[] compressedBytes = Base64.getDecoder().decode(compressed);

            try (ByteArrayInputStream byteInput = new ByteArrayInputStream(compressedBytes);
                 GZIPInputStream gzipInput = new GZIPInputStream(byteInput);
                 ByteArrayOutputStream byteOutput = new ByteArrayOutputStream()) {

                // Read decompressed data in chunks
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = gzipInput.read(buffer)) != -1) {
                    byteOutput.write(buffer, 0, bytesRead);
                }

                // Convert decompressed bytes to UTF-8 string
                String result = byteOutput.toString(StandardCharsets.UTF_8);

                if (log.isTraceEnabled()) {
                    log.trace("Decompressed string: {} bytes → {} bytes",
                            compressedBytes.length,
                            result.length());
                }

                return result;
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 input: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to decompress string (length: " + compressed.length() + ")", e);
        }
    }

    /**
     * Compresses a byte array using GZIP.
     *
     * Useful for:
     * - Compressing binary data
     * - Custom serialization formats
     * - File compression
     *
     * @param data the data to compress
     * @return GZIP compressed byte array
     * @throws IOException if compression fails
     * @throws IllegalArgumentException if input is null
     */
    public static byte[] compress(byte[] data) throws IOException {
        if (data == null) throw new IllegalArgumentException("Input data cannot be null");

        if (data.length == 0) return new byte[0];

        try (ByteArrayOutputStream byteOutput = new ByteArrayOutputStream(data.length);
             GZIPOutputStream gzipOutput = new GZIPOutputStream(byteOutput)) {

            gzipOutput.write(data);
            gzipOutput.finish();

            byte[] result = byteOutput.toByteArray();

            if (log.isTraceEnabled()) {
                log.trace("Compressed byte array: {} bytes → {} bytes ({}% reduction)",
                        data.length, result.length, 100 - (result.length * 100 / data.length));
            }

            return result;
        }
    }

    /**
     * Decompresses a GZIP-compressed byte array.
     *
     * @param compressed the compressed data
     * @return decompressed byte array
     * @throws IOException if decompression fails
     * @throws IllegalArgumentException if input is null
     */
    public static byte[] decompress(byte[] compressed) throws IOException {
        if (compressed == null) throw new IllegalArgumentException("Input data cannot be null");

        if (compressed.length == 0) return new byte[0];

        try (ByteArrayInputStream byteInput = new ByteArrayInputStream(compressed);
             GZIPInputStream gzipInput = new GZIPInputStream(byteInput);
             ByteArrayOutputStream byteOutput = new ByteArrayOutputStream()) {

            // Read decompressed data in chunks
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = gzipInput.read(buffer)) != -1) {
                byteOutput.write(buffer, 0, bytesRead);
            }

            byte[] result = byteOutput.toByteArray();

            if (log.isTraceEnabled()) {
                log.trace("Decompressed byte array: {} bytes → {} bytes", compressed.length, result.length);
            }

            return result;
        }
    }

    /**
     * Estimates the compression ratio for a given string.
     * Useful for deciding whether to enable compression.
     *
     * @param str the string to test
     * @return compression ratio (0.0 to 1.0, lower is better compression)
     */
    public static double estimateCompressionRatio(String str) {
        if (str == null || str.isEmpty()) return 1.0;

        try {
            String compressed = compress(str);
            int originalSize = str.getBytes(StandardCharsets.UTF_8).length;
            int compressedSize = Base64.getDecoder().decode(compressed).length;

            return (double) compressedSize / originalSize;
        } catch (Exception e) {
            log.warn("Failed to estimate compression ratio", e);
            return 1.0; // No compression benefit
        }
    }

    /**
     * Checks if compression would be beneficial for the given string.
     * Returns true if compression would save at least 20% space.
     *
     * @param str the string to check
     * @return true if compression is recommended
     */
    public static boolean shouldCompress(String str) {
        return estimateCompressionRatio(str) < 0.8; // 20% or better compression
    }

    /**
     * Returns human-readable size string.
     *
     * @param bytes size in bytes
     * @return formatted string (e.g., "1.5 KB", "2.3 MB")
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}