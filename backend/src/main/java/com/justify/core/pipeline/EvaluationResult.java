package com.justify.core.pipeline;

import com.justify.core.Status;

import java.util.List;
import java.util.Map;

/**
 * Outcome of one round.
 *
 * @param status           decided by code
 * @param fields           merged values (placeholders removed)
 * @param missingFields    required keys still missing, in template order
 * @param questions        key to question, at most {@code maxQuestions}, only when NEEDS_INFO
 * @param clarity          0-10
 * @param completeness     0.0-1.0
 * @param qualityScore     0.0-10.0
 * @param standardizedText rendered text, null while NEEDS_INFO
 * @param roundNo          1-based round this result belongs to
 */
public record EvaluationResult(
        Status status,
        Map<String, String> fields,
        List<String> missingFields,
        Map<String, String> questions,
        int clarity,
        double completeness,
        double qualityScore,
        String standardizedText,
        int roundNo) {
}
