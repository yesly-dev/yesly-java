package io.yesly.smoke;

import io.yesly.Page;
import io.yesly.Yesly;
import io.yesly.exception.InvalidRequestException;
import io.yesly.model.*;
import java.time.LocalDate;

/**
 * Calls the real API with a test key: one call per resource, then cleans up.
 *
 * <p>See "Smoke test against the real API" in the README for how to run it.
 */
public final class Smoke {
  public static void main(String[] args) {
    Yesly yesly = new Yesly();
    if (!"test".equals(yesly.mode())) throw new IllegalStateException("Use a ysk_test_ key.");
    String tomorrow = LocalDate.now().plusDays(1).toString();
    String externalId = "SDK-JAVA-" + System.currentTimeMillis();

    step("balance.retrieve", yesly.balance().retrieve().getObject());
    Page<AppointmentType> types = yesly.appointmentTypes().list();
    step("appointmentTypes.list", types.getData().get(0).getId());
    step("locations.list", yesly.locations().list().getData().size());

    Appointment apt = yesly.appointments().create(AppointmentCreateParams.builder()
        .externalId(externalId)
        .type(types.getData().get(0).getId())
        .customer("SDK smoke", "+34600000001")
        .slot(tomorrow + "T10:00:00+01:00", tomorrow + "T12:00:00+01:00")
        .putMetadata("source", "sdk-smoke")
        .build());
    step("appointments.create", apt.getId() + " " + apt.getStatus());
    step("appointments.retrieve", yesly.appointments().retrieve(apt.getId()).getExternalId());
    int n = 0;
    for (Appointment a : yesly.appointments().list(AppointmentListParams.builder().putMetadata("source", "sdk-smoke").limit(1L).build())) {
      if (++n >= 3) break;
    }
    step("appointments.list (auto-paging, limit 1)", n);
    step("appointments.update", yesly.appointments().update(apt.getId(), AppointmentUpdateParams.builder().putMetadata("note", "updated").build()).getMetadata().get("note"));
    step("appointments.cancel (no params)", yesly.appointments().cancel(apt.getId()).getStatus());

    WebhookEndpoint we = yesly.webhookEndpoints().create(WebhookEndpointCreateParams.builder().url("https://example.com/yesly-sdk-smoke").addEnabledEvent("appointment.confirmed").build());
    step("webhookEndpoints.create", we.getId());
    step("webhookEndpoints.rollSecret", yesly.webhookEndpoints().rollSecret(we.getId()).getId());
    step("webhookEndpoints.delete", yesly.webhookEndpoints().delete(we.getId()).getDeleted());

    Page<Event> events = yesly.events().list(EventListParams.builder().limit(5L).build());
    step("events.list", events.getData().size() + " events, first " + events.getData().get(0).getType());
    step("events.retrieve", yesly.events().retrieve(events.getData().get(0).getId()).getId());
    step("balance.listTransactions", yesly.balance().listTransactions(BalanceListTransactionsParams.builder().limit(5L).build()).getData().size());
    step("analytics.retrieveSummary", yesly.analytics().retrieveSummary(AnalyticsRetrieveSummaryParams.builder().range("7d").build()).getAppointments());

    try {
      yesly.appointments().create(AppointmentCreateParams.builder().externalId(externalId + "-bad").customer("x", "612").slot(tomorrow + "T10:00:00+01:00", null).build());
    } catch (InvalidRequestException e) {
      step("validation error", e.getClass().getSimpleName() + " " + e.getStatus() + " " + e.getCode() + " " + e.getParam() + (e.getRequestId() != null ? " with request id" : " NO REQUEST ID"));
    }
  }

  private static void step(String name, Object value) {
    System.out.println("ok  " + name + ": " + value);
  }
}
