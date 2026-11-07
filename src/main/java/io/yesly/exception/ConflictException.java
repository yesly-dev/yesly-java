package io.yesly.exception;

import java.util.List;
import java.util.Map;

/** 409: such as an {@code external_id} already used with different data. */
public class ConflictException extends YeslyException {
  public ConflictException(String message, Integer status, String type, String code, String param, String requestId, Map<String, List<String>> headers) {
    super(message, status, type, code, param, requestId, headers, null);
  }
}
