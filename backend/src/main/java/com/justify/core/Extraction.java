package com.justify.core;

import java.util.Map;

/**
 * What the model read from the free text.
 *
 * @param fields  field key to value, only keys of the template
 * @param clarity 0-10, how specific and actionable the text is
 */
public record Extraction(Map<String, String> fields, int clarity) {

    public Extraction {
        fields = Map.copyOf(fields);
        clarity = Math.max(0, Math.min(10, clarity));
    }
}
