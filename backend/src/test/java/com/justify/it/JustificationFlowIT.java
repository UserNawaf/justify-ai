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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Whole flow over HTTP against a real PostgreSQL. Skipped when Docker is not available. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@Import(JustificationFlowIT.FakeAi.class)
class JustificationFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("justify")
            .withInitScript("powerbi-role.sql");

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

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> post(String path, Map<String, ?> body) {
        return (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) http.postForEntity(path, body, Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void vagueTextToCompleteJustification() throws SQLException {
        Map<String, Object> submit = Map.of("templateId", "firewall-rule",
                "text", "port_protocol: TCP 443\nplease open it to the bank, urgent",
                "source", "jira", "externalRef", "OPS-42");

        // 1. vague text -> questions
        ResponseEntity<Map<String, Object>> r1 = post("/api/v1/justifications", submit);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) r1.getBody().get("id");
        assertThat(r1.getBody()).containsEntry("status", "NEEDS_INFO").containsEntry("round", 1);
        assertThat(((Map<String, String>) r1.getBody().get("questions")).keySet())
                .containsExactly("business_purpose", "source", "destination");

        // 2. connector retry -> same submission, no duplicate
        ResponseEntity<Map<String, Object>> retry = post("/api/v1/justifications", submit);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody()).containsEntry("id", id);

        // 3. answers, round 2 asks the rest
        ResponseEntity<Map<String, Object>> r2 = post("/api/v1/justifications/" + id + "/answers", Map.of("answers",
                Map.of("business_purpose", "Payroll app calls the bank payment API",
                        "source", "payroll-app-01 (10.10.5.20)",
                        "destination", "api.bank.example (203.0.113.10)")));
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r2.getBody()).containsEntry("status", "NEEDS_INFO").containsEntry("round", 2);
        assertThat(((Map<String, String>) r2.getBody().get("questions")).keySet())
                .containsExactly("duration", "impact_if_rejected");

        // 4. last answers -> COMPLETE
        ResponseEntity<Map<String, Object>> r3 = post("/api/v1/justifications/" + id + "/answers", Map.of("answers",
                Map.of("duration", "Permanent", "impact_if_rejected", "Salaries are paid late")));
        assertThat(r3.getBody()).containsEntry("status", "COMPLETE").containsEntry("round", 3);
        assertThat((String) r3.getBody().get("standardizedText"))
                .startsWith("Request type: Firewall rule request")
                .contains("Port / protocol: TCP 443")
                .contains("Duration: Permanent")
                .contains("Data classification: Not provided");

        // 5. closed -> 409
        assertThat(post("/api/v1/justifications/" + id + "/answers",
                Map.of("answers", Map.of("duration", "Temporary"))).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 6. stored, and visible in the Power BI views
        Map<String, Object> row = jdbc.queryForMap(
                "select is_complete, first_pass_complete, round_count, created_date from reporting.v_submissions where id = ?::uuid", id);
        assertThat(row).containsEntry("is_complete", true).containsEntry("first_pass_complete", false)
                .containsEntry("round_count", 3);
        assertThat(row.get("created_date")).isNotNull();
        assertThat(jdbc.queryForList(
                "select field_key from reporting.v_missing_fields where submission_id = ?::uuid and round_no = 1 order by field_key",
                String.class, id))
                .containsExactly("business_purpose", "destination", "duration", "impact_if_rejected", "source");
        assertThat(jdbc.queryForObject(
                "select count(*) from reporting.v_missing_fields where submission_id = ?::uuid", Long.class, id))
                .isEqualTo(7L);
    }

    @Test
    void firstPassCompleteAndDailySummary() {
        ResponseEntity<Map<String, Object>> r = post("/api/v1/justifications", Map.of("templateId", "firewall-rule",
                "text", """
                        business_purpose: Backups to the DR site
                        source: backup-01 (10.1.1.5)
                        destination: dr-backup-01 (10.9.1.5)
                        port_protocol: TCP 22
                        duration: Permanent
                        impact_if_rejected: No offsite backups
                        clarity: 9
                        """));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody()).containsEntry("status", "COMPLETE").containsEntry("qualityScore", 9.6);

        String id = (String) r.getBody().get("id");
        assertThat(jdbc.queryForObject("select first_pass_complete from reporting.v_submissions where id = ?::uuid",
                Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("select sum(total_requests) from reporting.v_daily_summary", Long.class))
                .isPositive();
    }

    @Test
    void modelDownStoresNothingAnd503() {
        Long before = jdbc.queryForObject("select count(*) from submission", Long.class);

        ResponseEntity<String> r = http.postForEntity("/api/v1/justifications",
                Map.of("templateId", "firewall-rule", "text", "#ai-down"), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getBody()).doesNotContain("internal-host");
        assertThat(jdbc.queryForObject("select count(*) from submission", Long.class)).isEqualTo(before);
    }

    @Test
    void unknownTemplateIs404() {
        assertThat(http.postForEntity("/api/v1/justifications",
                Map.of("templateId", "nope", "text", "x"), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void templatesAndStatsEndpoints() {
        assertThat(http.getForObject("/api/v1/templates/firewall-rule", String.class)).contains("port_protocol");
        assertThat(http.getForEntity("/api/v1/stats", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void powerBiReaderSeesViewsButNotTables() throws SQLException {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), "powerbi_reader", "reader-test")) {
            c.createStatement().executeQuery("select count(*) from reporting.v_submissions").close();
            c.createStatement().executeQuery("select count(*) from reporting.v_missing_fields").close();
            c.createStatement().executeQuery("select count(*) from reporting.v_daily_summary").close();
            assertThatThrownBy(() -> c.createStatement().executeQuery("select * from public.submission"))
                    .hasMessageContaining("permission denied");
        }
    }
}
