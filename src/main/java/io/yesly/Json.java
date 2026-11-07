package io.yesly;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The JSON mapper the library uses for requests, responses and webhooks. */
public final class Json {
  public static final ObjectMapper MAPPER =
      new ObjectMapper()
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private Json() {}

  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot write JSON", e);
    }
  }

  public static JsonNode read(String text) {
    try {
      return MAPPER.readTree(text);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  public static <T> T convert(JsonNode node, Class<T> type) {
    return MAPPER.convertValue(node, type);
  }
}
