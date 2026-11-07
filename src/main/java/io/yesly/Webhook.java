package io.yesly;

import io.yesly.exception.SignatureVerificationException;
import io.yesly.model.Event;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Webhook signature checks. They need the endpoint's signing secret ({@code whsec_…}), not an API key. */
public final class Webhook {
  /** Seconds a delivery's timestamp may differ from the local clock. */
  public static final long DEFAULT_TOLERANCE = 300;

  private Webhook() {}

  /** Verifies a delivery and returns its event. Pass the raw body, before any JSON parsing. */
  public static Event constructEvent(String payload, String header, String secret) {
    return constructEvent(payload, header, secret, DEFAULT_TOLERANCE, System.currentTimeMillis() / 1000);
  }

  public static Event constructEvent(String payload, String header, String secret, long tolerance, long now) {
    verifySignature(payload, header, secret, tolerance, now);
    Event event = Json.convert(Json.read(payload), Event.class);
    if (event == null) throw new SignatureVerificationException("The payload is not a JSON object.");
    return event;
  }

  /** Checks {@code Yesly-Signature} against the raw body. A tolerance of 0 turns the clock check off. */
  public static void verifySignature(String payload, String header, String secret, long tolerance, long now) {
    if (header == null || header.isEmpty()) throw new SignatureVerificationException("The Yesly-Signature header is missing.");
    Long timestamp = null;
    List<String> signatures = new ArrayList<>();
    for (String part : header.split(",")) {
      String[] kv = part.trim().split("=", 2);
      if (kv.length != 2) continue;
      if (kv[0].equals("t")) {
        try {
          timestamp = Long.parseLong(kv[1]);
        } catch (NumberFormatException e) {
          timestamp = null;
        }
      } else if (kv[0].equals("v1")) {
        signatures.add(kv[1]);
      }
    }
    if (timestamp == null || signatures.isEmpty()) throw new SignatureVerificationException("The Yesly-Signature header is malformed.");
    if (tolerance > 0 && Math.abs(now - timestamp) > tolerance) {
      throw new SignatureVerificationException("The Yesly-Signature timestamp is outside the tolerance.");
    }
    byte[] expected = hmac(secret, timestamp + "." + payload).getBytes(StandardCharsets.US_ASCII);
    for (String s : signatures) {
      if (MessageDigest.isEqual(expected, s.getBytes(StandardCharsets.US_ASCII))) return;
    }
    throw new SignatureVerificationException("No signature in Yesly-Signature matches the payload.");
  }

  /** Builds a {@code Yesly-Signature} value, to test your own webhook handler. */
  public static String generateTestHeader(String payload, String secret, long timestamp) {
    return "t=" + timestamp + ",v1=" + hmac(secret, timestamp + "." + payload);
  }

  private static String hmac(String secret, String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(raw.length * 2);
      for (byte b : raw) hex.append(String.format("%02x", b));
      return hex.toString();
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }
}
