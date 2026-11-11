package io.yesly;

import static org.junit.jupiter.api.Assertions.*;

import io.yesly.exception.SignatureVerificationException;
import io.yesly.model.Event;
import org.junit.jupiter.api.Test;

class WebhookTest {
  private static final String SECRET = "whsec_test";
  private static final long NOW = 1794474252L;
  private static final String BODY = "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"conversation.ended\",\"data\":{\"conversation\":{\"id\":\"cnv_7Qp4Ns2Xk9Lm3D\",\"outcome\":{\"code\":\"rescheduled\",\"data\":{\"delivery_window\":\"2026-12-03T09:00/13:00\"}}}}}";

  private static String header(String secret, long t) {
    return Webhook.generateTestHeader(BODY, secret, t);
  }

  @Test
  void validSignature() {
    Event e = Webhook.constructEvent(BODY, header(SECRET, NOW), SECRET, 300, NOW);
    assertEquals("conversation.ended", e.getType());
    assertEquals("rescheduled", e.getData().getConversation().getOutcome().getCode());
    assertEquals("2026-12-03T09:00/13:00", e.getData().getConversation().getOutcome().getData().get("delivery_window"));
  }

  @Test
  void matchesAnHmacComputedElsewhere() {
    // HMAC-SHA256 of "1794474252." + BODY with "whsec_test", computed with Python's hmac module.
    assertEquals("t=1794474252,v1=f60bd9a59d63d6f2d01cbdcd9aec4781af68e4d1e915aaff424817ce69921f83", header(SECRET, NOW));
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
