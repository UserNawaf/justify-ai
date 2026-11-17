package com.justify.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justify.core.Extraction;
import com.justify.core.Template;
import com.justify.testsupport.TestTemplates;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionParserTest {

    private final ExtractionParser parser = new ExtractionParser(new ObjectMapper());
    private final Template template = TestTemplates.firewall();

    @Test
    void parsesPlainJson() {
        Extraction e = parser.parse("""
                {"fields": {"port_protocol": "TCP 443", "source": null, "duration": "  Permanent "}, "clarity": 6}
                """, template);

        assertThat(e.fields()).containsOnly(
                java.util.Map.entry("port_protocol", "TCP 443"), java.util.Map.entry("duration", "Permanent"));
        assertThat(e.clarity()).isEqualTo(6);
    }

    @Test
    void acceptsCodeFencesAndChatter() {
        Extraction e = parser.parse("""
                Sure! Here is the JSON:
                ```json
                {"fields": {"port_protocol": "TCP 443"}, "clarity": "4"}
                ```
                Hope it helps.""", template);

        assertThat(e.fields()).containsEntry("port_protocol", "TCP 443");
        assertThat(e.clarity()).isEqualTo(4);
    }

    @Test
    void dropsInventedKeys() {
        Extraction e = parser.parse("{\"fields\": {\"colour\": \"blue\", \"source\": \"10.0.0.1\"}, \"clarity\": 5}",
                template);

        assertThat(e.fields()).containsOnlyKeys("source");
    }

    @Test
    void joinsArrayValues() {
        Extraction e = parser.parse("{\"fields\": {\"port_protocol\": [\"TCP 443\", \"TCP 8443\"]}, \"clarity\": 5}",
                template);

        assertThat(e.fields()).containsEntry("port_protocol", "TCP 443, TCP 8443");
    }

    @Test
    void rejectsMalformedReplies() {
        for (String bad : new String[]{
                "", "no json here", "{not json}", "{\"clarity\": 5}",
                "{\"fields\": [], \"clarity\": 5}", "{\"fields\": {}}",
                "{\"fields\": {}, \"clarity\": \"high\"}", "{\"fields\": {}, \"clarity\": 42}",
                "{\"fields\": {\"source\": {\"a\": 1}}, \"clarity\": 5}"}) {
            assertThatThrownBy(() -> parser.parse(bad, template))
                    .as(bad).isInstanceOf(ExtractionParser.MalformedReplyException.class);
        }
    }
}
