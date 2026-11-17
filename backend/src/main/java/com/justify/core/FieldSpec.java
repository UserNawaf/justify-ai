package com.justify.core;

import java.util.Objects;

/**
 * One field of a request template.
 *
 * @param key         stable machine key, e.g. {@code port_protocol}
 * @param label       English label used in the rendered justification (the engineer's fixed format)
 * @param required    whether the request is incomplete without it
 * @param question    English question asked when the field is missing
 * @param description what the field means, shown to the model
 * @param labelAr     Arabic label for the UI, or null
 * @param questionAr  Arabic question for the UI, or null
 */
public record FieldSpec(String key, String label, boolean required, String question, String description,
                        String labelAr, String questionAr) {

    public FieldSpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        if (question == null || question.isBlank()) {
            question = "Please provide: " + label;
        }
        if (description == null) {
            description = "";
        }
        if (labelAr != null && labelAr.isBlank()) {
            labelAr = null;
        }
        if (questionAr != null && questionAr.isBlank()) {
            questionAr = null;
        }
    }

    public FieldSpec(String key, String label, boolean required, String question, String description) {
        this(key, label, required, question, description, null, null);
    }

    /** Question in the requested language, falling back to English. */
    public String question(Language lang) {
        return lang == Language.AR && questionAr != null ? questionAr : question;
    }

    public String label(Language lang) {
        return lang == Language.AR && labelAr != null ? labelAr : label;
    }
}
