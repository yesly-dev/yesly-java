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
  private static final String CNV = "{\"id\":\"cnv_7Qp4Ns2Xk9Lm3D\",\"object\":\"conversation\",\"status\":\"scheduled\",\"channels\":[\"whatsapp\",\"call\"],\"context\":{\"order\":\"8841\"},\"cost\":0.0,\"unknown_field\":1}";
  private static final String LIST = "{\"object\":\"list\",\"url\":\"/v1/conversations\",\"has_more\":false,\"data\":[]}";

  private static Yesly client(FakeTransport t) {
    return new Yesly(ClientOptions.builder().apiKey("ysk_test_123").baseUrl("https://api.test/v1").transport(t).build()) {
      @Override
      protected void sleep(double seconds) {}
    };
  }

  @Test
  void createsAConversationAsInTheReadme() {
    FakeTransport t = new FakeTransport().answer(201, CNV);
    Yesly yesly = client(t);

    Conversation conversation =
        yesly.conversations().create(ConversationCreateParams.builder()
            .agent("agt_8Hd2kQ4mZ1LpXy")
            .contact(ContactInput.builder().externalId("customer-3391").name("Marta").phone("+34600000001").build())
            .addChannel("whatsapp")
            .addChannel("call")
            .startAt("2026-12-01T10:00:00+01:00")
            .putContext("order", "8841")
            .putContext("delivery_window", "2026-12-02T09:00/13:00")
            .build(),
            RequestOptions.builder().idempotencyKey("order-8841-delivery").build());

    assertEquals("cnv_7Qp4Ns2Xk9Lm3D", conversation.getId());
    assertEquals("scheduled", conversation.getStatus());
    assertEquals("8841", conversation.getContext().get("order"));
    FakeTransport.Call c = t.calls.get(0);
    assertEquals("POST", c.method);
    assertEquals("https://api.test/v1/conversations", c.url);
    assertEquals("Bearer ysk_test_123", c.headers.get("Authorization"));
    assertEquals(ApiVersion.CURRENT, c.headers.get("Yesly-Version"));
    assertTrue(c.headers.get("User-Agent").startsWith("yesly-java/" + Version.VERSION + " "));
    assertEquals("order-8841-delivery", c.headers.get("Idempotency-Key"));
    JsonNode body = Json.read(c.body);
    assertEquals("customer-3391", body.at("/contact/external_id").asText());
    assertEquals("call", body.at("/channels/1").asText());
    assertEquals("2026-12-02T09:00/13:00", body.at("/context/delivery_window").asText());
    assertFalse(body.at("/contact").has("email"), "unset fields are not sent");
  }

  @Test
  void aContactIdIsSentAsAString() {
    FakeTransport t = new FakeTransport().answer(201, CNV);
    client(t).conversations().create(ConversationCreateParams.builder().agent("agt_8Hd2kQ4mZ1LpXy").contact("ctc_3Kf9Lq2Wm7Rt4B").addChannel("call").build());
    assertEquals("ctc_3Kf9Lq2Wm7Rt4B", Json.read(t.calls.get(0).body).get("contact").asText());
  }

  @Test
  void encodesIdsAndQuery() {
    FakeTransport t = new FakeTransport().answer(200, CNV).answer(200, LIST).answer(200, "{\"id\":\"mem_1\",\"object\":\"memory_record\",\"deleted\":true}");
    Yesly y = client(t);
    y.conversations().retrieve("cnv/1");
    y.conversations().list(ConversationListParams.builder().status("no_reply").agent("agt_8Hd2kQ4mZ1LpXy").limit(5L).build());
    y.contacts().deleteMemory("ctc_3Kf9Lq2Wm7Rt4B", "mem/1");
    assertEquals("https://api.test/v1/conversations/cnv%2F1", t.calls.get(0).url);
    assertEquals("https://api.test/v1/conversations?agent=agt_8Hd2kQ4mZ1LpXy&status=no_reply&limit=5", t.calls.get(1).url);
    assertEquals("DELETE", t.calls.get(2).method);
    assertEquals("https://api.test/v1/contacts/ctc_3Kf9Lq2Wm7Rt4B/memory/mem%2F1", t.calls.get(2).url);
  }

  @Test
  void oneIdempotencyKeyAcrossRetries() {
    FakeTransport t = new FakeTransport()
        .answer(500, "{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")
        .answer(503, "{}", Map.of("retry-after", "0"))
        .answer(200, "{\"id\":\"ctc_3Kf9Lq2Wm7Rt4B\",\"object\":\"contact\"}");
    client(t).contacts().create(ContactCreateParams.builder().externalId("customer-3391").phone("+34600000001").build());
    assertEquals(3, t.calls.size());
    Set<String> keys = new HashSet<>();
    for (FakeTransport.Call c : t.calls) keys.add(c.headers.get("Idempotency-Key"));
    assertEquals(1, keys.size());
    assertTrue(keys.iterator().next().matches("[0-9a-f-]{36}"));
  }

  @Test
  void usesTheGivenIdempotencyKeyAndSendsNoBodyToCancel() {
    FakeTransport t = new FakeTransport().answer(200, CNV);
    client(t).conversations().cancel("cnv_7Qp4Ns2Xk9Lm3D", RequestOptions.builder().idempotencyKey("cancel-order-8841").build());
    FakeTransport.Call c = t.calls.get(0);
    assertEquals("https://api.test/v1/conversations/cnv_7Qp4Ns2Xk9Lm3D/cancel", c.url);
    assertEquals("cancel-order-8841", c.headers.get("Idempotency-Key"));
  }

  @Test
  void readsTurnsWithoutIdsInOnePage() {
    String turn = "{\"object\":\"turn\",\"role\":\"contact\",\"channel\":\"whatsapp\",\"message\":\"msg_1\",\"text\":\"Vale\",\"at\":\"2026-12-01T10:03:41+01:00\"}";
    FakeTransport t = new FakeTransport().answer(200, "{\"object\":\"list\",\"url\":\"/v1/conversations/cnv_1/messages\",\"has_more\":false,\"data\":[" + turn + "," + turn + "]}");
    Page<Turn> page = client(t).conversations().listMessages("cnv_1");
    assertFalse(page.hasNextPage());
    List<String> texts = new ArrayList<>();
    for (Turn x : page) texts.add(x.getText());
    assertEquals(List.of("Vale", "Vale"), texts);
    assertEquals(1, t.calls.size());
  }

  @Test
  void retriesThenGivesUpOnNetworkErrors() {
    FakeTransport t = new FakeTransport().answer(429, "{}", Map.of("retry-after", "0")).fail(new IOException("down")).fail(new IOException("down"));
    assertThrows(ApiConnectionException.class, () -> client(t).balance().retrieve());
    assertEquals(3, t.calls.size());
  }

  @Test
  void mapsErrorsAndDoesNotRetry4xx() {
    FakeTransport t = new FakeTransport().answer(422, "{\"error\":{\"type\":\"invalid_request\",\"code\":\"phone_invalid\",\"param\":\"contact.phone\",\"message\":\"Not E.164\",\"request_id\":\"req_1\"}}");
    InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> client(t).contacts().create(ContactCreateParams.builder().phone("612").build()));
    assertEquals(422, e.getStatus());
    assertEquals("phone_invalid", e.getCode());
    assertEquals("contact.phone", e.getParam());
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
        .answer(200, "{\"object\":\"list\",\"url\":\"/v1/events\",\"has_more\":true,\"data\":[{\"id\":\"evt_3\",\"type\":\"conversation.created\",\"data\":{\"conversation\":{\"id\":\"cnv_1\"}}},{\"id\":\"evt_2\"}]}")
        .answer(200, "{\"object\":\"list\",\"url\":\"/v1/events\",\"has_more\":false,\"data\":[{\"id\":\"evt_1\"}]}");
    Page<Event> first = client(t).events().list(EventListParams.builder().limit(2L).build());
    assertEquals(2, first.getData().size());
    assertEquals("cnv_1", first.getData().get(0).getData().getConversation().getId());
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

  @Test
  void baseUrlMustBeHttpsExceptForLocalhost() {
    for (String url : new String[] {"http://localhost:4010", "http://127.0.0.1:4010/v1", "http://[::1]:4010"}) {
      assertEquals(url, new Yesly(ClientOptions.builder().apiKey("ysk_test_1").baseUrl(url).build()).getBaseUrl());
    }
    YeslyException e = assertThrows(YeslyException.class, () -> new Yesly(ClientOptions.builder().apiKey("ysk_test_1").baseUrl("http://api.yesly.dev/v1").build()));
    assertTrue(e.getMessage().contains("must use https"));
  }
}
