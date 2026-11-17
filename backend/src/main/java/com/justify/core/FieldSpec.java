package com.justify.core;

import java.util.Objects;

/**
 * One field of a request template.
 *
 * @param key         stable machine key, e.g. {@code port_protocol}
 * @param label       human label used in the rendered justification
 * @param required    whether the request is incomplete without it
 * @param question    the question asked when the field is missing
 * @param description what the field means, shown to the model
 */
public record FieldSpec(String key, String label, boolean required, String question, String description) {

    public FieldSpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        if (question == null || question.isBlank()) {
            question = "Please provide: " + label;
        }
        if (description == null) {
            description = "";
        }
    }
}
