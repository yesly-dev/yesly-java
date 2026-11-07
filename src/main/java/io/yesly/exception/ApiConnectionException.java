package io.yesly.exception;

/** The request did not get an answer: network failure or timeout. */
public class ApiConnectionException extends YeslyException {
  public ApiConnectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
