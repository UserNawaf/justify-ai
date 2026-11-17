package com.justify.core.pipeline;

import com.justify.core.Extraction;
import com.justify.core.FieldSpec;
import com.justify.core.JustificationRenderer;
import com.justify.core.Placeholders;
import com.justify.core.QualityScorer;
import com.justify.core.Status;
import com.justify.core.Template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * extract -> merge -> decide -> score -> render.
 * The model is called only on the first round; answers are trusted as given.
 */
public final class EvaluationPipeline {

    private final AiClient aiClient;
    private final QualityScorer scorer;
    private final JustificationRenderer renderer;
    private final int maxQuestions;
    private final int maxRounds;

    public EvaluationPipeline(AiClient aiClient, QualityScorer scorer, JustificationRenderer renderer,
                              int maxQuestions, int maxRounds) {
        if (maxQuestions < 1 || maxRounds < 1) {
            throw new IllegalArgumentException("maxQuestions and maxRounds must be >= 1");
        }
        this.aiClient = Objects.requireNonNull(aiClient);
        this.scorer = Objects.requireNonNull(scorer);
        this.renderer = Objects.requireNonNull(renderer);
        this.maxQuestions = maxQuestions;
        this.maxRounds = maxRounds;
    }

    public int maxRounds() {
        return maxRounds;
    }

    /** Round 1: the employee's free text. */
    public EvaluationResult evaluateText(Template template, String text) {
        return evaluateText(template, text, Map.of());
    }

    /**
     * Round 1 with fields the caller already knows (a structured form, a ticket's own fields). Known values are
     * confirmed: they win over whatever the model reads (same rule as answers), so the model only has to find
     * what the form cannot capture, such as the business purpose.
     *
     * @throws IllegalArgumentException if a known field is not in the template
     */
    public EvaluationResult evaluateText(Template template, String text, Map<String, String> knownFields) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        Map<String, String> known = knownFields == null ? Map.of() : knownFields;
        for (String key : known.keySet()) {
            if (!template.hasField(key)) {
                throw new IllegalArgumentException("unknown field: " + key);
            }
        }
        Extraction extraction = aiClient.extract(template, text);
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldSpec spec : template.fields()) {
            String confirmed = known.get(spec.key());
            String value = extraction.fields().get(spec.key());
            if (!Placeholders.isMissing(confirmed)) {
                fields.put(spec.key(), confirmed.strip());
            } else if (!Placeholders.isMissing(value)) {
                fields.put(spec.key(), value.strip());
            }
        }
        return decide(template, fields, extraction.clarity(), 1);
    }

    /**
     * Round 2+: answers to the questions. Earlier values are kept, confirmed answers win.
     *
     * @throws IllegalArgumentException if an answer names a field the template does not have
     */
    public EvaluationResult applyAnswers(Template template, Map<String, String> previousFields,
                                         int previousClarity, Map<String, String> answers, int roundNo) {
        if (answers == null || answers.isEmpty()) {
            throw new IllegalArgumentException("answers must not be empty");
        }
        for (String key : answers.keySet()) {
            if (!template.hasField(key)) {
                throw new IllegalArgumentException("unknown field: " + key);
            }
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldSpec spec : template.fields()) {
            String answer = answers.get(spec.key());
            String previous = previousFields == null ? null : previousFields.get(spec.key());
            if (!Placeholders.isMissing(answer)) {
                fields.put(spec.key(), answer.strip());
            } else if (!Placeholders.isMissing(previous)) {
                fields.put(spec.key(), previous.strip());
            }
        }
        return decide(template, fields, previousClarity, roundNo);
    }

    private EvaluationResult decide(Template template, Map<String, String> fields, int clarity, int roundNo) {
        List<FieldSpec> required = template.requiredFields();
        List<String> missing = new ArrayList<>();
        for (FieldSpec spec : required) {
            if (!fields.containsKey(spec.key())) {
                missing.add(spec.key());
            }
        }
        double completeness = required.isEmpty() ? 1.0 : (double) (required.size() - missing.size()) / required.size();
        double score = scorer.score(completeness, clarity);

        Status status;
        Map<String, String> questions = new LinkedHashMap<>();
        String text = null;
        if (missing.isEmpty()) {
            status = Status.COMPLETE;
        } else if (roundNo >= maxRounds) {
            status = Status.INCOMPLETE;
        } else {
            status = Status.NEEDS_INFO;
            for (String key : missing.subList(0, Math.min(maxQuestions, missing.size()))) {
                questions.put(key, template.field(key).orElseThrow().question());
            }
        }
        if (status.isClosed()) {
            text = renderer.render(template, fields);
        }
        return new EvaluationResult(status, Collections.unmodifiableMap(fields), List.copyOf(missing),
                Collections.unmodifiableMap(questions), clarity, completeness, score, text, roundNo);
    }
}
