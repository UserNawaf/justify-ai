package com.justify.ai;

import com.justify.core.FieldSpec;
import com.justify.core.Template;

/** Builds the extraction prompt. Change it, then run PromptEvalIT and compare the numbers (docs/TESTING.md). */
public final class PromptBuilder {

    public String systemPrompt(Template template) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You read a free-text justification that an employee wrote for an IT request and extract facts from it.
                The text may be in Arabic, English or a mix.

                Request type: %s
                %s

                Fields to extract:
                """.formatted(template.name(), template.description()));
        for (FieldSpec f : template.fields()) {
            sb.append("- ").append(f.key())
                    .append(f.required() ? " (required)" : " (optional)")
                    .append(": ").append(f.label());
            if (!f.description().isBlank()) {
                sb.append(". ").append(f.description());
            }
            sb.append('\n');
        }
        sb.append("""

                Rules:
                1. Only use information that is explicitly written in the text. Never guess, infer or invent a value.
                   When in doubt, use null: a missing value is asked for later, a wrong value is not.
                2. If a field is not stated in the text, its value is null. In particular:
                   - urgency ("urgent", "asap", "ضروري") is NOT a duration; duration is null unless the text says
                     permanent/temporary or gives an end date or period;
                   - impact_if_rejected is null unless the text says what happens if the request is refused;
                   - business_purpose is null unless the text says why the access is needed; naming the systems
                     involved is not a purpose; "manager approved" is not a purpose.
                3. Write values in short, plain English. Copy hostnames, IP addresses, URLs, ports and dates exactly.
                4. "clarity" is an integer 0-10: how specific and actionable the text is for the engineer who must
                   implement it (0 = useless, 10 = nothing to ask). Vague words like "the server" or "the bank" lower it.
                5. Reply with JSON only, no explanation, exactly in this shape:
                {"fields": {""");
        boolean first = true;
        for (FieldSpec f : template.fields()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append('"').append(f.key()).append("\": string or null");
            first = false;
        }
        sb.append("}, \"clarity\": integer}\n");
        return sb.toString();
    }

    public String userPrompt(String text) {
        return "Justification:\n\"\"\"\n" + text.strip() + "\n\"\"\"";
    }
}
