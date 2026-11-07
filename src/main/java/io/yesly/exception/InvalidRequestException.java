package io.yesly.exception;

import java.util.List;
import java.util.Map;

/** 400 and other 4xx answers: something in the request needs fixing. */
public class InvalidRequestException extends YeslyException {
  public InvalidRequestException(String message, Integer status, String type, String code, String param, String requestId, Map<String, List<String>> headers) {
    super(message, status, type, code, param, requestId, headers, null);
  }
}
