package io.yesly.exception;

import java.util.List;
import java.util.Map;

/** 5xx: something went wrong on Yesly's side. Retried automatically before this is thrown. */
public class ApiException extends YeslyException {
  public ApiException(String message, Integer status, String type, String code, String param, String requestId, Map<String, List<String>> headers) {
    super(message, status, type, code, param, requestId, headers, null);
  }
}
