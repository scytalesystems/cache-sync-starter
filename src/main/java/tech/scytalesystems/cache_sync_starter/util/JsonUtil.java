package tech.scytalesystems.cache_sync_starter.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.scytalesystems.cache_sync_starter.config.JacksonConfig;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1148h
 * <p>
 * A utility class for JSON serialization and deserialization.
 * It uses a singleton {@link ObjectMapper} instance for efficiency.
 */
@Component
public class JsonUtil {
    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);
    private static final ObjectMapper MAPPER = new JacksonConfig().objectMapper();

    /**
     * Serializes an object to a JSON string.
     *
     * @param o The object to serialize.
     * @return The JSON string, or null if serialization fails.
     */
    public static String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON: {}", o, e);

            return null;
        }
    }

    /**
     * Deserializes a JSON string to an object.
     *
     * @param json  The JSON string.
     * @param clazz The class of the target object.
     * @param <T>   The type of the target object.
     * @return The deserialized object, or null if deserialization fails.
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON to object: {}", json, e);

            return null;
        }
    }
}
