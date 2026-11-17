package com.justify.firewall;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FirewallRequestView(
        UUID id,
        String ticketNo,
        RequestStatus status,
        String requesterName,
        String employeeId,
        String department,
        String email,
        String sourceHostname,
        String sourceIp,
        String destinationHost,
        String destinationIp,
        FirewallForm.Protocol protocol,
        String ports,
        FirewallForm.DurationType durationType,
        LocalDate endDate,
        Justification justification,
        String decidedBy,
        String decisionNote,
        Instant decidedAt,
        Instant createdAt,
        Instant updatedAt) {

    /** The AI-checked part of the request. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Justification(UUID id, String status, int round, int maxRounds, Map<String, String> questions,
                                List<String> missingFields, String standardizedText, double qualityScore,
                                String originalText) {
    }

    /** One row of the requests table. */
    public record Summary(UUID id, String ticketNo, RequestStatus status, String requesterName, String department,
                          String source, String destination, String service, Double qualityScore,
                          Instant createdAt) {
    }
}
