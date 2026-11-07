package io.yesly.exception;

import java.util.List;
import java.util.Map;

/** 429: too many requests. Retried automatically before this is thrown. */
public class RateLimitException extends YeslyException {
  public RateLimitException(String message, Integer status, String type, String code, String param, String requestId, Map<String, List<String>> headers) {
    super(message, status, type, code, param, requestId, headers, null);
  }
}
