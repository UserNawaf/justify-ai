package com.justify.firewall;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FirewallFormValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
    private final FirewallFormValidator validator = new FirewallFormValidator();

    static FirewallForm form(String srcIp, String srcHost, String destIp, String destHost, String ports,
                             FirewallForm.DurationType type, LocalDate start, LocalDate end) {
        return new FirewallForm("Nawaf", "E1", "IT", null, FirewallForm.RequestType.NEW_ACCESS,
                FirewallForm.Priority.MEDIUM, "Payroll System", srcIp, srcHost, destIp, destHost, ports,
                FirewallForm.Protocol.TCP, FirewallForm.Environment.PRODUCTION, "because", type, start, end, null);
    }

    static FirewallForm valid() {
        return form("10.10.5.20", "payroll-app-01", "203.0.113.10", "api.bank.example", "443",
                FirewallForm.DurationType.PERMANENT, TODAY, null);
    }

    private List<String> fields(FirewallForm f) {
        return validator.validate(f, TODAY).stream().map(FirewallFormValidator.Problem::field).toList();
    }

    @Test
    void validFormsHaveNoProblems() {
        assertThat(validator.validate(valid(), TODAY)).isEmpty();
        assertThat(fields(form("10.0.0.0/24", null, "172.16.5.10", "https://api.bank.example:8443/pay",
                "443, 8443, 9000-9010", FirewallForm.DurationType.TEMPORARY, TODAY.plusDays(3), TODAY.plusDays(30))))
                .isEmpty();
        assertThat(fields(form("10.0.0.1", "", "10.9.9.9", "", "22", FirewallForm.DurationType.PERMANENT,
                TODAY.plusDays(1), null))).as("hostnames are optional").isEmpty();
    }

    @Test
    void rejectsBadAddresses() {
        assertThat(fields(form("10.10.5.300", "bad host!", "1.2.3", "not a host", "443",
                FirewallForm.DurationType.PERMANENT, TODAY, null)))
                .containsExactly("sourceIp", "sourceHostname", "destinationIp", "destinationHost");
        assertThat(fields(form("0.0.0.0/0", null, "10.0.0.1", null, "443", FirewallForm.DurationType.PERMANENT, TODAY, null)))
                .containsExactly("sourceIp");
        assertThat(fields(form("10.0.0.1", null, "0.0.0.0/0", null, "443", FirewallForm.DurationType.PERMANENT, TODAY, null)))
                .containsExactly("destinationIp");
        assertThat(fields(form("10.0.0.0/33", null, "10.0.0.1", null, "443", FirewallForm.DurationType.PERMANENT, TODAY, null)))
                .containsExactly("sourceIp");
    }

    @Test
    void rejectsBadPorts() {
        for (String p : new String[]{"0", "65536", "http", "443-80", "1-5000", "443;80", ""}) {
            assertThat(FirewallFormValidator.portProblem(p)).as(p).isNotNull();
        }
        assertThat(FirewallFormValidator.portProblem("1-1002")).isNotNull();
        assertThat(FirewallFormValidator.portProblem("8000-9000")).isNull();
        assertThat(FirewallFormValidator.normalisePorts("443,8443 ,  9000-9010")).isEqualTo("443, 8443, 9000-9010");
    }

    @Test
    void datesAreChecked() {
        assertThat(fields(form("10.0.0.1", null, "10.0.0.2", null, "443", FirewallForm.DurationType.PERMANENT,
                TODAY.minusDays(1), null))).containsExactly("startDate");
        assertThat(fields(form("10.0.0.1", null, "10.0.0.2", null, "443", FirewallForm.DurationType.TEMPORARY,
                TODAY, null))).containsExactly("endDate");
        assertThat(fields(form("10.0.0.1", null, "10.0.0.2", null, "443", FirewallForm.DurationType.TEMPORARY,
                TODAY.plusDays(10), TODAY.plusDays(5)))).containsExactly("endDate");
        assertThat(fields(form("10.0.0.1", null, "10.0.0.2", null, "443", FirewallForm.DurationType.TEMPORARY,
                TODAY, TODAY.plusDays(400)))).containsExactly("endDate");
    }

    @Test
    void formBecomesConfirmedJustificationFields() {
        Map<String, String> known = FirewallRequestService.knownFields(valid(), "443");
        assertThat(known).containsExactly(
                Map.entry("source", "payroll-app-01 (10.10.5.20)"),
                Map.entry("destination", "api.bank.example (203.0.113.10)"),
                Map.entry("port_protocol", "TCP 443"),
                Map.entry("duration", "Permanent, from 2026-11-17"));
        assertThat(FirewallRequestService.knownFields(form("10.0.0.1", null, "10.0.0.2", " ", "22",
                FirewallForm.DurationType.TEMPORARY, TODAY, LocalDate.of(2026, 12, 31)), "22"))
                .containsEntry("duration", "Temporary, 2026-11-17 to 2026-12-31")
                .containsEntry("source", "10.0.0.1")
                .containsEntry("destination", "10.0.0.2");
    }
}
