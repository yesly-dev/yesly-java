package io.yesly;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.yesly.exception.ApiConnectionException;
import io.yesly.exception.AuthenticationException;
import io.yesly.exception.ConflictException;
import io.yesly.exception.InvalidRequestException;
import io.yesly.exception.YeslyException;
import io.yesly.model.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClientTest {
  private static final String APT = "{\"id\":\"apt_1\",\"object\":\"appointment\",\"external_id\":\"ORD-48213\",\"status\":\"scheduled\",\"cost\":0.0,\"unknown_field\":1}";

  private static Yesly client(FakeTransport t) {
    return new Yesly(ClientOptions.builder().apiKey("ysk_test_123").baseUrl("https://api.test/v1").transport(t).build()) {
      @Override
      protected void sleep(double seconds) {}
    };
  }

  @Test
  void createsAnAppointmentAsOnTheWebsite() {
    FakeTransport t = new FakeTransport().answer(201, APT);
    Yesly yesly = client(t);

    Appointment apt =
        yesly.appointments().create(AppointmentCreateParams.builder()
            .externalId("ORD-48213")
            .type("delivery")
            .customer("Martha", "+34612345678")
            .slot("2026-11-13T09:00:00+01:00", "2026-11-13T11:00:00+01:00")
            .putMetadata("order_id", "PV-18342")
            .putMetadata("route_id", "BIO-07")
            .build());

    assertEquals("apt_1", apt.getId());
    assertEquals("scheduled", apt.getStatus());
    FakeTransport.Call c = t.calls.get(0);
    assertEquals("POST", c.method);
    assertEquals("https://api.test/v1/appointments", c.url);
    assertEquals("Bearer ysk_test_123", c.headers.get("Authorization"));
    assertEquals(ApiVersion.CURRENT, c.headers.get("Yesly-Version"));
    assertTrue(c.headers.get("User-Agent").startsWith("yesly-java/" + Version.VERSION + " "));
    assertFalse(c.headers.containsKey("Idempotency-Key"));
    JsonNode body = Json.read(c.body);
    assertEquals("Martha", body.at("/customer/name").asText());
    assertEquals("2026-11-13T11:00:00+01:00", body.at("/slot/end").asText());
    assertEquals("BIO-07", body.at("/metadata/route_id").asText());
    assertFalse(body.has("location"), "unset fields are not sent");
  }

  @Test
  void encodesIdsAndQuery() {
    FakeTransport t = new FakeTransport().answer(200, APT).answer(200, "{\"object\":\"list\",\"url\":\"/v1/appointments\",\"has_more\":false,\"data\":[]}").answer(200, "{\"object\":\"list\",\"data\":[]}");
    Yesly y = client(t);
    y.appointments().retrieve("apt/1");
    y.appointments().list(AppointmentListParams.builder().status("no_reply").putMetadata("route_id", "BIO-07").limit(5L).build());
    y.slots().list(SlotListParams.builder().available(true).build());
    assertEquals("https://api.test/v1/appointments/apt%2F1", t.calls.get(0).url);
    assertEquals("https://api.test/v1/appointments?status=no_reply&metadata%5Broute_id%5D=BIO-07&limit=5", t.calls.get(1).url);
    assertEquals("https://api.test/v1/slots?available=true", t.calls.get(2).url);
  }

  @Test
  void oneIdempotencyKeyAcrossRetries() {
    FakeTransport t = new FakeTransport()
        .answer(500, "{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")
        .answer(503, "{}", Map.of("retry-after", "0"))
        .answer(200, "{\"object\":\"list\",\"data\":[]}");
    client(t).slots().create(SlotCreateParams.builder()
        .addSlot(SlotCreate.builder().start("2026-11-13T09:00:00+01:00").end("2026-11-13T11:00:00+01:00").type("delivery").build())
        .build());
    assertEquals(3, t.calls.size());
    Set<String> keys = new HashSet<>();
    for (FakeTransport.Call c : t.calls) keys.add(c.headers.get("Idempotency-Key"));
    assertEquals(1, keys.size());
    assertTrue(keys.iterator().next().matches("[0-9a-f-]{36}"));
  }

  @Test
  void usesTheGivenIdempotencyKeyAndSendsAnEmptyObject() {
    FakeTransport t = new FakeTransport().answer(200, APT).answer(200, APT);
    Yesly y = client(t);
    y.appointments().cancel("apt_1", AppointmentCancelParams.builder().reason("Shop closed").build(), RequestOptions.builder().idempotencyKey("cancel-apt_1").build());
    y.appointments().cancel("apt_2");
    assertEquals("cancel-apt_1", t.calls.get(0).headers.get("Idempotency-Key"));
    assertEquals("{\"reason\":\"Shop closed\"}", t.calls.get(0).body);
    assertEquals("{}", t.calls.get(1).body);
  }

  @Test
  void retriesThenGivesUpOnNetworkErrors() {
    FakeTransport t = new FakeTransport().answer(429, "{}", Map.of("retry-after", "0")).fail(new IOException("down")).fail(new IOException("down"));
    assertThrows(ApiConnectionException.class, () -> client(t).balance().retrieve());
    assertEquals(3, t.calls.size());
  }

  @Test
  void mapsErrorsAndDoesNotRetry4xx() {
    FakeTransport t = new FakeTransport().answer(422, "{\"error\":{\"type\":\"invalid_request\",\"code\":\"phone_invalid\",\"param\":\"customer.phone\",\"message\":\"Not E.164\",\"request_id\":\"req_1\"}}");
    InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> client(t).appointments().retrieve("apt_1"));
    assertEquals(422, e.getStatus());
    assertEquals("phone_invalid", e.getCode());
    assertEquals("customer.phone", e.getParam());
    assertEquals("req_1", e.getRequestId());
    assertEquals(1, t.calls.size());
  }

  @Test
  void statusClasses() {
    String err = "{\"error\":{\"type\":\"x\",\"message\":\"m\"}}";
    Yesly y = client(new FakeTransport().answer(401, err).answer(409, err));
    assertThrows(AuthenticationException.class, () -> y.events().retrieve("evt_1"));
    assertThrows(ConflictException.class, () -> y.events().retrieve("evt_1"));
  }

  @Test
  void iteratingAPageWalksEveryPage() {
    FakeTransport t = new FakeTransport()
        .answer(200, "{\"object\":\"list\",\"url\":\"/v1/events\",\"has_more\":true,\"data\":[{\"id\":\"evt_3\",\"type\":\"appointment.created\",\"data\":{\"appointment\":{\"id\":\"apt_1\"}}},{\"id\":\"evt_2\"}]}")
        .answer(200, "{\"object\":\"list\",\"url\":\"/v1/events\",\"has_more\":false,\"data\":[{\"id\":\"evt_1\"}]}");
    Page<Event> first = client(t).events().list(EventListParams.builder().limit(2L).build());
    assertEquals(2, first.getData().size());
    assertEquals("apt_1", first.getData().get(0).getData().getAppointment().getId());
    List<String> ids = new ArrayList<>();
    for (Event e : first) ids.add(e.getId());
    assertEquals(List.of("evt_3", "evt_2", "evt_1"), ids);
    assertEquals("https://api.test/v1/events?limit=2&starting_after=evt_2", t.calls.get(1).url);
  }

  @Test
  void needsAKey() {
    Yesly y = new Yesly(ClientOptions.builder().transport(new FakeTransport()).build());
    if (System.getenv("YESLY_API_KEY") == null) {
      YeslyException e = assertThrows(YeslyException.class, () -> y.balance().retrieve());
      assertTrue(e.getMessage().contains("No API key"));
    }
    assertEquals("test", new Yesly("ysk_test_1").mode());
  }
}
