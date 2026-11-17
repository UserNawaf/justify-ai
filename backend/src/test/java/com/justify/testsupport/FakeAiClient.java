package com.justify.testsupport;

import com.justify.core.AiUnavailableException;
import com.justify.core.Extraction;
import com.justify.core.Template;
import com.justify.core.pipeline.AiClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic stand-in for the model: reads "field: value" lines. "clarity: N" sets clarity (default 7).
 * Text containing "#ai-down" simulates an unavailable model.
 */
public class FakeAiClient implements AiClient {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public Extraction extract(Template template, String text) {
        calls.incrementAndGet();
        if (text.contains("#ai-down")) {
            throw new AiUnavailableException("simulated outage at http://internal-host:11434");
        }
        Map<String, String> fields = new HashMap<>();
        int clarity = 7;
        for (String line : text.split("\\R")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            if (key.equals("clarity")) {
                clarity = Integer.parseInt(value);
            } else if (template.hasField(key)) {
                fields.put(key, value);
            }
        }
        return new Extraction(fields, clarity);
    }

    public int calls() {
        return calls.get();
    }
}
