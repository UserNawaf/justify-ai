package com.justify.testsupport;

import com.justify.core.Template;
import com.justify.template.YamlTemplateParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class TestTemplates {

    private TestTemplates() {
    }

    /** The bundled firewall template, so tests also validate the real file. */
    public static Template firewall() {
        try (InputStream in = TestTemplates.class.getResourceAsStream("/templates/firewall-rule.yml")) {
            return new YamlTemplateParser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    "firewall-rule.yml");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
