package com.justify.firewall;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FirewallFormValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
    private final FirewallFormValidator validator = new FirewallFormValidator();

    static FirewallForm form(String srcHost, String srcIp, String dest, String destIp, String ports,
                             FirewallForm.DurationType type, LocalDate end) {
        return new FirewallForm("Nawaf", "E1", "IT", null, srcHost, srcIp, dest, destIp, FirewallForm.Protocol.TCP,
                ports, type, end, "because");
    }

    static FirewallForm valid() {
        return form("payroll-app-01", "10.10.5.20", "api.bank.example", "203.0.113.10", "443",
                FirewallForm.DurationType.PERMANENT, null);
    }

    private List<String> fields(FirewallForm f) {
        return validator.validate(f, TODAY).stream().map(FirewallFormValidator.Problem::field).toList();
    }

    @Test
    void validFormHasNoProblems() {
        assertThat(validator.validate(valid(), TODAY)).isEmpty();
        assertThat(fields(form("db.corp.local", "10.0.0.0/24", "https://api.bank.example:8443/pay", "", "443, 8443, 9000-9010",
                FirewallForm.DurationType.TEMPORARY, TODAY.plusDays(30)))).isEmpty();
        assertThat(fields(form("h1", "10.0.0.1", "10.9.9.9", null, "22", FirewallForm.DurationType.PERMANENT, null))).isEmpty();
    }

    @Test
    void rejectsBadAddresses() {
        assertThat(fields(form("bad host!", "10.10.5.300", "not a host", "1.2.3", "443",
                FirewallForm.DurationType.PERMANENT, null)))
                .containsExactly("sourceHostname", "sourceIp", "destinationHost", "destinationIp");
        assertThat(fields(form("h", "0.0.0.0/0", "x.example", null, "443", FirewallForm.DurationType.PERMANENT, null)))
                .containsExactly("sourceIp");
        assertThat(fields(form("h", "10.0.0.0/33", "x.example", null, "443", FirewallForm.DurationType.PERMANENT, null)))
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
    void temporaryRuleNeedsAFutureEndDateWithinAYear() {
        assertThat(fields(form("h", "10.0.0.1", "x.example", null, "443", FirewallForm.DurationType.TEMPORARY, null)))
                .containsExactly("endDate");
        assertThat(fields(form("h", "10.0.0.1", "x.example", null, "443", FirewallForm.DurationType.TEMPORARY, TODAY)))
                .containsExactly("endDate");
        assertThat(fields(form("h", "10.0.0.1", "x.example", null, "443", FirewallForm.DurationType.TEMPORARY,
                TODAY.plusDays(400)))).containsExactly("endDate");
    }

    @Test
    void formBecomesConfirmedJustificationFields() {
        Map<String, String> known = FirewallRequestService.knownFields(valid(), "443");
        assertThat(known).containsExactly(
                Map.entry("source", "payroll-app-01 (10.10.5.20)"),
                Map.entry("destination", "api.bank.example (203.0.113.10)"),
                Map.entry("port_protocol", "TCP 443"),
                Map.entry("duration", "Permanent"));
        assertThat(FirewallRequestService.knownFields(form("h", "10.0.0.1", "x.example", null, "22",
                FirewallForm.DurationType.TEMPORARY, LocalDate.of(2026, 12, 31)), "22"))
                .containsEntry("duration", "Temporary until 2026-12-31")
                .containsEntry("destination", "x.example");
    }
}
