package io.yesly.smoke;

import io.yesly.Page;
import io.yesly.RequestOptions;
import io.yesly.Yesly;
import io.yesly.exception.InvalidRequestException;
import io.yesly.model.*;

/**
 * Calls the real API with a test key: one call per resource, then cleans up.
 *
 * <p>See "Smoke test against the real API" in the README for how to run it.
 */
public final class Smoke {
  public static void main(String[] args) {
    Yesly yesly = new Yesly();
    if (!"test".equals(yesly.mode())) throw new IllegalStateException("Use a ysk_test_ key.");
    String externalId = "SDK-JAVA-" + System.currentTimeMillis();

    step("balance.retrieve", yesly.balance().retrieve().getObject());
    Page<Agent> agents = yesly.agents().list();
    step("agents.list", agents.getData().size());
    step("senders.list", yesly.senders().list().getData().size());
    step("templates.list", yesly.templates().list().getData().size());

    Contact contact = yesly.contacts().create(ContactCreateParams.builder()
        .externalId(externalId).name("SDK smoke").phone("+34600000001").putMetadata("source", "sdk-smoke").build());
    step("contacts.create", contact.getId());
    step("contacts.create again (same contact)",
        yesly.contacts().create(ContactCreateParams.builder().externalId(externalId).phone("+34600000001").build()).getId().equals(contact.getId()));
    step("contacts.retrieve", yesly.contacts().retrieve(contact.getId()).getExternalId());
    step("contacts.update", yesly.contacts().update(contact.getId(), ContactUpdateParams.builder().putMetadata("note", "updated").build()).getMetadata().get("note"));
    int n = 0;
    for (Contact c : yesly.contacts().list(ContactListParams.builder().limit(1L).build())) {
      if (++n >= 3) break;
    }
    step("contacts.list (auto-paging, limit 1)", n);
    step("contacts.listMemory", yesly.contacts().listMemory(contact.getId()).getData().size());

    Agent agent = agents.getData().stream().filter(a -> "active".equals(a.getStatus())).findFirst().orElse(null);
    if (agent != null) {
      ConversationCreateParams params = ConversationCreateParams.builder()
          .agent(agent.getId()).contact(contact.getId()).addChannel(agent.getChannels().get(0)).build();
      RequestOptions key = RequestOptions.builder().idempotencyKey(externalId + "-cnv").build();
      Conversation cnv = yesly.conversations().create(params, key);
      step("conversations.create", cnv.getId() + " " + cnv.getStatus());
      step("conversations.create again (replayed)", yesly.conversations().create(params, key).getId().equals(cnv.getId()));
      step("conversations.retrieve", yesly.conversations().retrieve(cnv.getId()).getStatus());
      step("conversations.list", yesly.conversations().list(ConversationListParams.builder().contact(contact.getId()).build()).getData().size());
      step("conversations.listMessages", yesly.conversations().listMessages(cnv.getId()).getData().size());
      step("messages.list", yesly.messages().list(MessageListParams.builder().conversation(cnv.getId()).build()).getData().size());
    } else {
      step("conversations.*", "skipped: the account has no active agent");
    }

    OptOut optOut = yesly.optOuts().create(OptOutCreateParams.builder().channel("sms").address("+34600000099").build());
    step("optOuts.create", optOut.getId());
    step("optOuts.delete", yesly.optOuts().delete(optOut.getId()).getDeleted());

    WebhookEndpoint we = yesly.webhookEndpoints().create(WebhookEndpointCreateParams.builder().url("https://example.com/yesly-sdk-smoke").addEnabledEvent("conversation.ended").build());
    step("webhookEndpoints.create", we.getId());
    step("webhookEndpoints.rollSecret", yesly.webhookEndpoints().rollSecret(we.getId()).getId());
    step("webhookEndpoints.delete", yesly.webhookEndpoints().delete(we.getId()).getDeleted());

    Page<Event> events = yesly.events().list(EventListParams.builder().limit(5L).build());
    step("events.list", events.getData().size());
    if (!events.getData().isEmpty()) step("events.retrieve", yesly.events().retrieve(events.getData().get(0).getId()).getId());
    step("balance.listTransactions", yesly.balance().listTransactions(BalanceListTransactionsParams.builder().limit(5L).build()).getData().size());
    step("analytics.summary", yesly.analytics().summary(AnalyticsSummaryParams.builder().range("7d").build()).getConversations());

    try {
      yesly.contacts().create(ContactCreateParams.builder().externalId(externalId + "-bad").phone("612").build());
    } catch (InvalidRequestException e) {
      step("validation error", e.getClass().getSimpleName() + " " + e.getStatus() + " " + e.getCode() + " " + e.getParam() + (e.getRequestId() != null ? " with request id" : " NO REQUEST ID"));
    }
    step("contacts.delete", yesly.contacts().delete(contact.getId()).getDeleted());
  }

  private static void step(String name, Object value) {
    System.out.println("ok  " + name + ": " + value);
  }
}
