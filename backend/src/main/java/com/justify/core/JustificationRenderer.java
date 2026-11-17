package com.justify.core;

import java.util.Map;

/** Renders the fixed-format justification. Code, not the model, writes the wording (D3). */
public final class JustificationRenderer {

    public static final String NOT_PROVIDED = "Not provided";

    public String render(Template template, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("Request type: ").append(template.name());
        for (FieldSpec field : template.fields()) {
            String value = fields.get(field.key());
            sb.append('\n').append(field.label()).append(": ")
                    .append(Placeholders.isMissing(value) ? NOT_PROVIDED : oneLine(value));
        }
        return sb.toString();
    }

    private static String oneLine(String value) {
        return value.strip().replaceAll("\\s*\\R\\s*", "; ");
    }
}
