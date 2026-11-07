package io.yesly.exception;

import java.util.List;
import java.util.Map;

/** 401: missing or revoked API key. */
public class AuthenticationException extends YeslyException {
  public AuthenticationException(String message, Integer status, String type, String code, String param, String requestId, Map<String, List<String>> headers) {
    super(message, status, type, code, param, requestId, headers, null);
  }
}
