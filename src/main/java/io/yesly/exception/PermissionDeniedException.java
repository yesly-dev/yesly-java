package io.yesly.exception;

import java.util.List;
import java.util.Map;

/** 403: the key cannot reach this resource, or the IP is not allowed. */
public class PermissionDeniedException extends YeslyException {
  public PermissionDeniedException(String message, Integer status, String type, String code, String param, String requestId, Map<String, List<String>> headers) {
    super(message, status, type, code, param, requestId, headers, null);
  }
}
