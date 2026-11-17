package com.justify.core;

import com.justify.testsupport.TestTemplates;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JustificationRendererTest {

    private final JustificationRenderer renderer = new JustificationRenderer();

    @Test
    void rendersFixedFormatInTemplateOrder() {
        String text = renderer.render(TestTemplates.firewall(), Map.of(
                "port_protocol", "TCP 443",
                "business_purpose", "Payroll app must call the bank's payment API for monthly salaries",
                "source", "payroll-app-01 (10.10.5.20)",
                "destination", "api.bank.example (203.0.113.10)",
                "duration", "Permanent",
                "impact_if_rejected", "Salaries cannot be paid on time"));

        assertThat(text).isEqualTo("""
                Request type: Firewall rule request
                Business purpose: Payroll app must call the bank's payment API for monthly salaries
                Source: payroll-app-01 (10.10.5.20)
                Destination: api.bank.example (203.0.113.10)
                Port / protocol: TCP 443
                Duration: Permanent
                Impact if rejected: Salaries cannot be paid on time
                Data classification: Not provided""");
    }

    @Test
    void missingAndPlaceholderValuesAreNotProvided() {
        String text = renderer.render(TestTemplates.firewall(), Map.of("source", "N/A"));

        assertThat(text.lines().skip(1)).allMatch(l -> l.endsWith(": Not provided"));
    }

    @Test
    void multiLineValuesStayOnOneLine() {
        String text = renderer.render(TestTemplates.firewall(), Map.of("source", "host-a\n  host-b"));

        assertThat(text).contains("Source: host-a; host-b");
    }
}
