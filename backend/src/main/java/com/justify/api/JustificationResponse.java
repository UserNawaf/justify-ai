package com.justify.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.justify.core.Status;
import com.justify.service.SubmissionSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JustificationResponse(
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
        Instant completedAt) {

    static JustificationResponse of(SubmissionSnapshot s) {
        return new JustificationResponse(s.id(), s.templateId(), s.source(), s.externalRef(), s.status(), s.round(),
                s.maxRounds(), s.questions(), s.missingFields(), s.fields(), s.standardizedText(), s.qualityScore(),
                s.completeness(), s.clarity(), s.createdAt(), s.updatedAt(), s.completedAt());
    }
}
