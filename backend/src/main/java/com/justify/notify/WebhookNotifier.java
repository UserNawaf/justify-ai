package com.justify.notify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justify.service.CompletionListener;
import com.justify.service.SubmissionSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Generic outbound webhook (EPIC 5): POSTs a signed JSON event to one URL when a submission closes, so any
 * system (ServiceNow, Jira, Teams flow) can pick up the standardised text without polling.
 * Runs off the request thread; a failing receiver never affects the employee. Retries with backoff, then logs.
 */
public class WebhookNotifier implements CompletionListener {

    public static final String EVENT = "submission.closed";
    public static final String SIGNATURE_HEADER = "X-Justify-Signature";

    private static final Logger log = LoggerFactory.getLogger(WebhookNotifier.class);

    private final URI url;
    private final byte[] secret;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Executor executor;
    private final List<Duration> backoff;
    private final Duration timeout;

    public WebhookNotifier(URI url, String secret, HttpClient http, ObjectMapper mapper, Executor executor,
                           List<Duration> backoff, Duration timeout) {
        this.url = url;
        this.secret = secret == null || secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
        this.http = http;
        this.mapper = mapper;
        this.executor = executor;
        this.backoff = List.copyOf(backoff);
        this.timeout = timeout;
    }

    @Override
    public void onClosed(SubmissionSnapshot s) {
        String body;
        try {
            body = mapper.writeValueAsString(payload(s));
        } catch (JsonProcessingException e) {
            log.error("Webhook payload for {} could not be serialised", s.id(), e);
            return;
        }
        executor.execute(() -> deliver(s, body));
    }

    static Map<String, Object> payload(SubmissionSnapshot s) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("event", EVENT);
        p.put("id", s.id());
        p.put("templateId", s.templateId());
        p.put("source", s.source());
        p.put("externalRef", s.externalRef());
        p.put("status", s.status());
        p.put("round", s.round());
        p.put("qualityScore", s.qualityScore());
        p.put("missingFields", s.missingFields());
        p.put("standardizedText", s.standardizedText());
        p.put("completedAt", s.completedAt() == null ? null : s.completedAt().toString());
        return p;
    }

    /** {@code sha256=<hex HMAC of the raw body>}, like GitHub webhooks. Receivers should verify it. */
    public static String sign(byte[] secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private void deliver(SubmissionSnapshot s, String body) {
        HttpRequest.Builder req = HttpRequest.newBuilder(url)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-Justify-Event", EVENT)
                .header("X-Justify-Delivery", s.id().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (secret != null) {
            req.header(SIGNATURE_HEADER, sign(secret, body));
        }
        HttpRequest request = req.build();
        int attempts = backoff.size() + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<Void> res = http.send(request, HttpResponse.BodyHandlers.discarding());
                if (res.statusCode() / 100 == 2) {
                    log.info("Webhook delivered for {} ({})", s.id(), res.statusCode());
                    return;
                }
                if (res.statusCode() / 100 == 4 && res.statusCode() != 408 && res.statusCode() != 429) {
                    log.warn("Webhook for {} rejected with {}, not retrying", s.id(), res.statusCode());
                    return;
                }
                log.warn("Webhook for {} got {} (attempt {}/{})", s.id(), res.statusCode(), attempt, attempts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Webhook for {} failed (attempt {}/{}): {}", s.id(), attempt, attempts, e.toString());
            }
            if (attempt < attempts) {
                try {
                    Thread.sleep(backoff.get(attempt - 1).toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("Webhook for {} gave up after {} attempts", s.id(), attempts);
    }
}
