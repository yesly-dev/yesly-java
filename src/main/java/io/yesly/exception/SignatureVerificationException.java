package io.yesly.exception;

/** A webhook delivery whose {@code Yesly-Signature} does not match, or is too old. */
public class SignatureVerificationException extends YeslyException {
  public SignatureVerificationException(String message) {
    super(message);
  }
}
