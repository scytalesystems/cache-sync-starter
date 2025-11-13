package tech.scytalesystems.cache_sync_starter.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1310h
 * <p>Gzip Compression Utility
 */
public class GzipUtils {
    public static String compress(String str) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
            gzip.close();

            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String decompress(String compressed) {
        byte[] data = Base64.getDecoder().decode(compressed);

        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data));
             BufferedReader br = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {

            return br.lines().collect(Collectors.joining());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
