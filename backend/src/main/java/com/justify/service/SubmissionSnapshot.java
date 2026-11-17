package com.justify.service;

import com.justify.core.Status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read model of a submission, built inside the transaction. */
public record SubmissionSnapshot(
        UUID id,
        String templateId,
        String source,
        String externalRef,
        Status status,
        int round,
        int maxRounds,
        Map<String, String> questions,
        List<String> missingFields,
        Map<String, String> fields,
        String standardizedText,
        double qualityScore,
        double completeness,
        int clarity,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        boolean created,
        String rawText) {
}
