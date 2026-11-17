package com.justify.it;

import com.justify.core.pipeline.AiClient;
import com.justify.testsupport.FakeAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The firewall request portal end to end, against real PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@Import(FirewallPortalIT.FakeAi.class)
@SuppressWarnings("unchecked")
class FirewallPortalIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("justify");

    @TestConfiguration
    static class FakeAi {
        @Bean
        @Primary
        AiClient fakeAiClient() {
            return new FakeAiClient();
        }
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    private static Map<String, Object> form(String justification) {
        Map<String, Object> f = new HashMap<>();
        f.put("requesterName", "Nawaf");
        f.put("employeeId", "E10234");
        f.put("department", "Finance IT");
        f.put("email", "nawaf@example.com");
        f.put("sourceHostname", "payroll-app-01");
        f.put("sourceIp", "10.10.5.20");
        f.put("destinationHost", "api.bank.example");
        f.put("destinationIp", "203.0.113.10");
        f.put("protocol", "TCP");
        f.put("ports", "443,8443");
        f.put("durationType", "PERMANENT");
        f.put("justification", justification);
        return f;
    }

    private ResponseEntity<Map<String, Object>> post(String path, Object body) {
        return (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) http.postForEntity(path, body, Map.class);
    }

    @Test
    void vagueJustificationThenAnswersThenApproval() {
        // 1. form is fine, justification is vague: only the "why" is asked, never the technical fields
        ResponseEntity<Map<String, Object>> created = post("/api/v1/firewall-requests?lang=ar", form("ضروري الله يعافيكم"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> r = created.getBody();
        String id = (String) r.get("id");
        assertThat((String) r.get("ticketNo")).matches("FW-\\d{4}-\\d{5}");
        assertThat(r).containsEntry("status", "AWAITING_INFO").containsEntry("ports", "443, 8443");
        Map<String, Object> j = (Map<String, Object>) r.get("justification");
        assertThat(((Map<String, String>) j.get("questions")).keySet())
                .containsExactly("business_purpose", "impact_if_rejected");
        assertThat(((Map<String, String>) j.get("questions")).get("business_purpose")).contains("الغرض");

        // 2. engineer cannot decide yet
        assertThat(post("/api/v1/firewall-requests/" + id + "/decision",
                Map.of("decision", "APPROVED", "engineer", "Sara")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 3. requester answers -> engineer's queue, justification complete with the form values in it
        Map<String, Object> answered = post("/api/v1/firewall-requests/" + id + "/answers", Map.of("answers", Map.of(
                "business_purpose", "Send the monthly salary file to the bank",
                "impact_if_rejected", "Salaries are paid late"))).getBody();
        assertThat(answered).containsEntry("status", "PENDING_REVIEW");
        String text = (String) ((Map<String, Object>) answered.get("justification")).get("standardizedText");
        assertThat(text).contains("Source: payroll-app-01 (10.10.5.20)")
                .contains("Port / protocol: TCP 443, 8443")
                .contains("Duration: Permanent")
                .contains("Business purpose: Send the monthly salary file to the bank");

        // 4. rejection needs a note; approval works once; second decision is a conflict
        assertThat(post("/api/v1/firewall-requests/" + id + "/decision",
                Map.of("decision", "REJECTED", "engineer", "Sara")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> approved = post("/api/v1/firewall-requests/" + id + "/decision",
                Map.of("decision", "APPROVED", "engineer", "Sara", "note", "Opened on FW-DMZ-2")).getBody();
        assertThat(approved).containsEntry("status", "APPROVED").containsEntry("decidedBy", "Sara");
        assertThat(post("/api/v1/firewall-requests/" + id + "/decision",
                Map.of("decision", "REJECTED", "engineer", "Sara", "note", "x")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // 5. findable by ticket, listed, reported
        String ticket = (String) r.get("ticketNo");
        assertThat(http.getForObject("/api/v1/firewall-requests/ticket/" + ticket.toLowerCase(), Map.class))
                .containsEntry("id", id);
        Map<String, Object> list = http.getForObject("/api/v1/firewall-requests?status=APPROVED&q=payroll", Map.class);
        assertThat((List<Map<String, Object>>) list.get("items")).anySatisfy(item -> {
            assertThat(item).containsEntry("ticketNo", ticket).containsEntry("service", "TCP 443, 8443");
            assertThat(item.get("qualityScore")).isNotNull();
        });
        assertThat(jdbc.queryForObject("select status from reporting.v_firewall_requests where ticket_no = ?",
                String.class, ticket)).isEqualTo("APPROVED");
    }

    @Test
    void clearJustificationGoesStraightToReview() {
        Map<String, Object> r = post("/api/v1/firewall-requests", form("""
                business_purpose: Backups to the DR site
                impact_if_rejected: No offsite backups
                clarity: 9""")).getBody();

        assertThat(r).containsEntry("status", "PENDING_REVIEW");
        Map<String, Object> j = (Map<String, Object>) r.get("justification");
        assertThat(j).containsEntry("status", "COMPLETE").doesNotContainKey("questions");
        assertThat((String) j.get("originalText")).contains("Backups to the DR site");

        Map<String, Object> mine = http.getForObject("/api/v1/firewall-requests?employeeId=E10234&status=PENDING_REVIEW",
                Map.class);
        assertThat(((Number) mine.get("total")).longValue()).isPositive();
    }

    @Test
    void invalidFormListsEveryProblemAndStoresNothing() {
        Long before = jdbc.queryForObject("select count(*) from firewall_request", Long.class);
        Map<String, Object> bad = form("because");
        bad.put("sourceIp", "10.10.5.300");
        bad.put("ports", "http");
        bad.put("durationType", "TEMPORARY");
        bad.put("requesterName", "");

        ResponseEntity<Map<String, Object>> r = post("/api/v1/firewall-requests", bad);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((List<Map<String, String>>) r.getBody().get("errors")).extracting(e -> e.get("field"))
                .contains("requesterName");

        bad.put("requesterName", "Nawaf");
        r = post("/api/v1/firewall-requests", bad);
        assertThat((List<Map<String, String>>) r.getBody().get("errors")).extracting(e -> e.get("field"))
                .containsExactlyInAnyOrder("sourceIp", "ports", "endDate");

        bad.put("sourceIp", "10.10.5.20");
        bad.put("ports", "443");
        bad.put("endDate", LocalDate.now().plusDays(10).toString());
        assertThat(post("/api/v1/firewall-requests", bad).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject("select count(*) from firewall_request", Long.class)).isEqualTo(before + 1);
    }
}
