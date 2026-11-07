package io.yesly.exception;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Base class of every exception this library throws. */
public class YeslyException extends RuntimeException {
  private final Integer status;
  private final String type;
  private final String code;
  private final String param;
  private final String requestId;
  private final Map<String, List<String>> headers;

  public YeslyException(String message) {
    this(message, null, null, null, null, null, null, null);
  }

  public YeslyException(String message, Throwable cause) {
    this(message, null, null, null, null, null, null, cause);
  }

  public YeslyException(
      String message,
      Integer status,
      String type,
      String code,
      String param,
      String requestId,
      Map<String, List<String>> headers,
      Throwable cause) {
    super(message, cause);
    this.status = status;
    this.type = type;
    this.code = code;
    this.param = param;
    this.requestId = requestId;
    this.headers = headers == null ? Collections.emptyMap() : headers;
  }

  /** HTTP status, when the API answered. */
  public Integer getStatus() {
    return status;
  }

  /** Error class from the API, such as {@code invalid_request}. */
  public String getType() {
    return type;
  }

  /** Machine-readable reason, such as {@code phone_invalid}. */
  public String getCode() {
    return code;
  }

  /** The parameter at fault, such as {@code customer.phone}. */
  public String getParam() {
    return param;
  }

  /** {@code Request-Id} of the failed request; include it when you contact support. */
  public String getRequestId() {
    return requestId;
  }

  public Map<String, List<String>> getHeaders() {
    return headers;
  }

  /** Builds the exception for an error answer. */
  public static YeslyException fromResponse(int status, JsonNode body, Map<String, List<String>> headers) {
    JsonNode e = body != null && body.path("error").isObject() ? body.path("error") : null;
    String message = text(e, "message");
    if (message == null) message = "The Yesly API answered " + status + ".";
    String requestId = text(e, "request_id");
    if (requestId == null) {
      List<String> h = headers.get("request-id");
      requestId = h == null || h.isEmpty() ? null : h.get(0);
    }
    String type = text(e, "type");
    String code = text(e, "code");
    String param = text(e, "param");
    switch (status) {
      case 401:
        return new AuthenticationException(message, status, type, code, param, requestId, headers);
      case 403:
        return new PermissionDeniedException(message, status, type, code, param, requestId, headers);
      case 404:
        return new NotFoundException(message, status, type, code, param, requestId, headers);
      case 409:
        return new ConflictException(message, status, type, code, param, requestId, headers);
      case 429:
        return new RateLimitException(message, status, type, code, param, requestId, headers);
      default:
        return status >= 500
            ? new ApiException(message, status, type, code, param, requestId, headers)
            : new InvalidRequestException(message, status, type, code, param, requestId, headers);
    }
  }

  private static String text(JsonNode node, String field) {
    if (node == null) return null;
    JsonNode v = node.get(field);
    return v != null && v.isTextual() ? v.asText() : null;
  }
}
