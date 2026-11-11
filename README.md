# yesly-java

The official Java library for the Yesly API.

> Status: 0.2.0, not published yet. Covers API version `2026-11-11`.

## Install

Gradle:

```kotlin
implementation("io.yesly:yesly-java:0.2.0")
```

Maven:

```xml
<dependency>
  <groupId>io.yesly</groupId>
  <artifactId>yesly-java</artifactId>
  <version>0.2.0</version>
</dependency>
```

Java 11 or later. Depends on Jackson (`jackson-databind`).

## Usage

Yesly runs agents built for your company. The usual integration is two calls:
ask an agent to contact someone, and receive the outcome on a webhook.

```java
Yesly yesly = new Yesly(System.getenv("YESLY_API_KEY"));

// Create the contact, or update it by your own id.
Contact contact = yesly.contacts().create(ContactCreateParams.builder()
    .externalId("customer-3391")
    .name("Marta")
    .phone("+34600000001")
    .build());

// Ask the delivery agent to contact her: WhatsApp first, then a call.
Conversation conversation = yesly.conversations().create(
    ConversationCreateParams.builder()
        .agent("agt_8Hd2kQ4mZ1LpXy")
        .contact(contact.getId())
        .addChannel("whatsapp")
        .addChannel("call")
        .startAt("2026-12-01T10:00:00+01:00")
        .putContext("order", "8841")
        .putContext("delivery_window", "2026-12-02T09:00/13:00")
        .build(),
    RequestOptions.builder().idempotencyKey("order-8841-delivery").build());
conversation.getStatus(); // "scheduled"
```

`contact` also takes the data to find or create one
(`ContactInput.builder().externalId(…).phone(…).build()`), so the first call
is optional. `new Yesly()` reads `YESLY_API_KEY`. Every resource of the API
reference has an accessor: `agents()`, `senders()`, `contacts()`,
`conversations()`, `messages()`, `templates()`, `optOuts()`, `analytics()`,
`balance()`, `webhookEndpoints()` and `events()`. Parameters are `…Params`
classes with builders, and responses are model classes (`io.yesly.model`)
with getters; unknown fields are ignored.

## Integration checklist

These are the points an integration most often gets wrong; check them before
going live. The whole API reference is also in Markdown, for agents and other
tools: [llms-full.txt](https://yesly.dev/llms-full.txt) (index in
[llms.txt](https://yesly.dev/llms.txt), OpenAPI in
[openapi.yaml](https://yesly.dev/openapi.yaml)).

1. **Read your agent first** (`yesly.agents().retrieve`). It declares the
   `outcomes` it can end with and the `context` it expects; a context that
   does not fit is a `422` `context_invalid` with the field in `param`.
2. **Use your own id as the contact's `external_id`.** Contacts are matched
   only by exact identifiers (`external_id`, then a phone, then an email), and
   `contacts().create` adds what is new to the one it finds. An identifier
   that belongs to another contact is a `409` `contact_identifier_taken`:
   merge the two first (`yesly.contacts().merge`).
3. **Send an `Idempotency-Key` with every new conversation**, derived from your
   own data (`order-8841-delivery`). The library adds a random one to each
   call; your own makes a retry after a crash safe too.
4. **Send times with an offset** (`2026-12-01T10:00:00+01:00`) and phones in
   E.164. Live contacts must be in Spain, and not premium or special-rate
   numbers (`phone_not_allowed`).
5. **Handle the outcome on a webhook.** Verify the signature over the raw
   body, answer `2xx` within 10 seconds before doing slow work, and skip event
   ids you have already handled: a delivery can arrive more than once.
   `conversation.ended` carries the conversation with its `outcome`
   (`code` and `data`); `conversation.no_reply`, `conversation.cancelled` and
   `conversation.failed` close the others.
6. **Build and test with a `ysk_test_` key.** The test phone numbers choose
   what happens (`+34600000001` answers and the agent ends with its first
   outcome, `+34600000009` never answers; the full table is in the reference)
   and waits last seconds.
7. **Retry only what can succeed.** The library already retries network
   errors, `408`, `429` and `5xx`, with the same idempotency key. A `4xx`
   fails the same way again: fix the request using the error's `code` and
   `param`.
8. **Keep keys on the server.** A read-only key can list and retrieve but not
   write (`api_key_read_only`); a key limited to one agent gets `403`
   `api_key_agent_scope` on any other.

## Webhooks

```java
try {
  Event event = Webhook.constructEvent(rawBody, request.getHeader("Yesly-Signature"), System.getenv("YESLY_WEBHOOK_SECRET"));
  if (event.getType().equals("conversation.ended")) {
    Conversation c = event.getData().getConversation();
    Outcome outcome = c.getOutcome(); // code "rescheduled", data {delivery_window=2026-12-03T09:00/13:00}
  }
} catch (SignatureVerificationException e) {
  response.setStatus(400);
}
```

Pass the raw body as a string. Deliveries more than five minutes old are
rejected, and either signature is accepted during a secret roll.
`Webhook.generateTestHeader` signs a payload to test your handler.
A delivery can arrive more than once, and a captured one stays valid for
those five minutes, so skip events whose `id` you have already handled.

## Lists

A list call returns its first `Page`. Iterating the page walks every page:

```java
Page<Event> page = yesly.events().list(EventListParams.builder().limit(50L).build());
page.getData(); page.getHasMore(); page.nextPage();

for (Conversation c : yesly.conversations().list(ConversationListParams.builder().agent("agt_8Hd2kQ4mZ1LpXy").status("no_reply").build())) {
  System.out.println(c.getId() + " " + c.getContext());
}
```

## Errors

Failed requests throw an unchecked `io.yesly.exception.YeslyException`
subclass: `InvalidRequestException` (400, 422), `AuthenticationException` (401),
`PermissionDeniedException` (403), `NotFoundException` (404),
`ConflictException` (409), `RateLimitException` (429), `ApiException` (5xx) or
`ApiConnectionException` (no answer). Each has `getStatus()`, `getType()`,
`getCode()`, `getParam()` and `getRequestId()`.

## Retries, timeouts and idempotency

Network errors, timeouts, 408, 429 and 5xx are retried twice with backoff,
honouring `Retry-After`. POSTs that take an `Idempotency-Key` get one per call,
reused on every retry; pass your own with `RequestOptions`.

```java
Yesly yesly = new Yesly(ClientOptions.builder().apiKey(key).timeout(Duration.ofSeconds(10)).maxRetries(4).build());
yesly.conversations().cancel("cnv_7Qp4Ns2Xk9Lm3D", RequestOptions.builder().idempotencyKey("cancel-order-8841").build());
```

## Test mode

A `ysk_test_` key runs everything in test mode; `yesly.mode()` says which. Use
the test phone numbers from the API reference (`+34600000001` answers on the
first channel and the agent ends with its first outcome, and so on).

## Development

```bash
mvn verify
```

`src/main/java/io/yesly/model/`, `src/main/java/io/yesly/service/`,
`Yesly.java` and `ApiVersion.java` are generated from the OpenAPI
specification. Do not edit them: change `yesly-openapi`, then, with both repos
side by side,

```bash
(cd ../yesly-openapi && npm install)
rm -rf src/main/java/io/yesly/model src/main/java/io/yesly/service
node ../yesly-openapi/sdk/generate.mjs java .
```

### Smoke test against the real API

```bash
mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
YESLY_API_KEY=ysk_test_… \
  java -cp target/classes:target/test-classes:$(cat target/cp.txt) io.yesly.smoke.Smoke
```

## Releasing

Bump `<version>` in `pom.xml` and `Version.VERSION`, then push a tag
`v<version>`.
