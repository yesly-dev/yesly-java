package io.yesly;

import static org.junit.jupiter.api.Assertions.*;

import io.yesly.exception.SignatureVerificationException;
import io.yesly.model.Event;
import org.junit.jupiter.api.Test;

class WebhookTest {
  private static final String SECRET = "whsec_test";
  private static final long NOW = 1794474252L;
  private static final String BODY = "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"appointment.confirmed\",\"data\":{\"appointment\":{\"id\":\"apt_1\",\"external_id\":\"ORD-48213\"}}}";

  private static String header(String secret, long t) {
    return Webhook.generateTestHeader(BODY, secret, t);
  }

  @Test
  void validSignature() {
    Event e = Webhook.constructEvent(BODY, header(SECRET, NOW), SECRET, 300, NOW);
    assertEquals("appointment.confirmed", e.getType());
    assertEquals("ORD-48213", e.getData().getAppointment().getExternalId());
  }

  @Test
  void matchesAnHmacComputedElsewhere() {
    // HMAC-SHA256 of "1794474252." + BODY with "whsec_test", computed with Python's hmac module.
    assertEquals("t=1794474252,v1=62b8ce65d20d98e1d1d8f14ff76317b0bf9b166a3dc5c2d980022e54c62e0a67", header(SECRET, NOW));
  }

  @Test
  void eitherSignatureDuringARoll() {
    String old = header("whsec_old", NOW).split(",")[1];
    String h = "t=" + NOW + "," + old + "," + header(SECRET, NOW).split(",")[1];
    assertEquals("evt_1", Webhook.constructEvent(BODY, h, SECRET, 300, NOW).getId());
  }

  @Test
  void rejections() {
    String h = header(SECRET, NOW);
    assertThrows(SignatureVerificationException.class, () -> Webhook.constructEvent(BODY + " ", h, SECRET, 300, NOW));
    assertThrows(SignatureVerificationException.class, () -> Webhook.constructEvent(BODY, h, "whsec_other", 300, NOW));
    SignatureVerificationException old = assertThrows(SignatureVerificationException.class, () -> Webhook.constructEvent(BODY, h, SECRET, 300, NOW + 301));
    assertTrue(old.getMessage().contains("tolerance"));
    assertNotNull(Webhook.constructEvent(BODY, h, SECRET, 0, NOW + 3600));
    assertTrue(assertThrows(SignatureVerificationException.class, () -> Webhook.constructEvent(BODY, "nonsense", SECRET, 300, NOW)).getMessage().contains("malformed"));
    assertTrue(assertThrows(SignatureVerificationException.class, () -> Webhook.constructEvent(BODY, null, SECRET, 300, NOW)).getMessage().contains("missing"));
  }
}
