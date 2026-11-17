package com.justify.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justify.core.Status;
import com.justify.service.SubmissionSnapshot;
import com.justify.testsupport.WebhookReceiver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookNotifierTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static SubmissionSnapshot closed() {
        Instant now = Instant.parse("2026-11-17T10:00:00Z");
        return new SubmissionSnapshot(UUID.randomUUID(), "firewall-rule", "servicenow", "RITM0012345",
                Status.COMPLETE, 2, 3, Map.of(), List.of(), Map.of(), "Request type: Firewall rule request",
                9.2, 1.0, 8, now, now, now, false);
    }

    private WebhookNotifier notifier(WebhookReceiver receiver, String secret, int retries) {
        return new WebhookNotifier(URI.create(receiver.url()), secret, HttpClient.newHttpClient(), mapper,
                Runnable::run, java.util.Collections.nCopies(retries, Duration.ofMillis(10)), Duration.ofSeconds(5));
    }

    @Test
    void postsSignedEvent() throws Exception {
        try (WebhookReceiver receiver = new WebhookReceiver(0)) {
            SubmissionSnapshot s = closed();
            notifier(receiver, "top-secret", 0).onClosed(s);

            assertThat(receiver.received()).hasSize(1);
            WebhookReceiver.Received r = receiver.received().get(0);
            JsonNode json = mapper.readTree(r.body());
            assertThat(json.get("event").asText()).isEqualTo("submission.closed");
            assertThat(json.get("externalRef").asText()).isEqualTo("RITM0012345");
            assertThat(json.get("status").asText()).isEqualTo("COMPLETE");
            assertThat(json.get("standardizedText").asText()).startsWith("Request type:");
            assertThat(r.header("X-Justify-Signature"))
                    .isEqualTo(WebhookNotifier.sign("top-secret".getBytes(StandardCharsets.UTF_8), r.body()));
            assertThat(r.header("X-Justify-Delivery")).isEqualTo(s.id().toString());
        }
    }

    @Test
    void retriesOnServerErrors() {
        try (WebhookReceiver receiver = new WebhookReceiver(2)) {
            notifier(receiver, null, 3).onClosed(closed());

            assertThat(receiver.hits()).isEqualTo(3);
            assertThat(receiver.received()).hasSize(1);
            assertThat(receiver.received().get(0).header("X-Justify-Signature")).isNull();
        }
    }

    @Test
    void givesUpQuietlyWhenReceiverIsDown() {
        WebhookReceiver receiver = new WebhookReceiver(0);
        String url = receiver.url();
        receiver.close();

        new WebhookNotifier(URI.create(url), "s", HttpClient.newHttpClient(), mapper, Runnable::run,
                List.of(Duration.ofMillis(5)), Duration.ofSeconds(1)).onClosed(closed()); // must not throw
    }

    @Test
    void signatureMatchesKnownValue() {
        // echo -n '{}' | openssl dgst -sha256 -hmac key
        assertThat(WebhookNotifier.sign("key".getBytes(StandardCharsets.UTF_8), "{}"))
                .isEqualTo("sha256=a777724d943eb48dc69bca8a4a6d57a04db3f9ec7e1de4e581e860265bdf3032");
    }
}
