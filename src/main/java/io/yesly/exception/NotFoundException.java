package io.yesly.exception;

import java.util.List;
import java.util.Map;

/** 404: no such object in this account and mode. */
public class NotFoundException extends YeslyException {
  public NotFoundException(String message, Integer status, String type, String code, String param, String requestId, Map<String, List<String>> headers) {
    super(message, status, type, code, param, requestId, headers, null);
  }
}
