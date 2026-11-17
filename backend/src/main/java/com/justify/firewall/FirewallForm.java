package com.justify.firewall;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** What the requester fills in. Technical fields are validated by code; the justification is checked by AI. */
public record FirewallForm(
        @Schema(example = "Nawaf Alotaibi") @NotBlank @Size(max = 120) String requesterName,
        @Schema(example = "E10234") @NotBlank @Size(max = 40) String employeeId,
        @Schema(example = "Finance IT") @NotBlank @Size(max = 120) String department,
        @Schema(example = "nawaf@example.com") @Email @Size(max = 200) String email,
        @Schema(example = "payroll-app-01") @NotBlank @Size(max = 255) String sourceHostname,
        @Schema(example = "10.10.5.20") @NotBlank @Size(max = 64) String sourceIp,
        @Schema(example = "api.bank.example") @NotBlank @Size(max = 255) String destinationHost,
        @Schema(example = "203.0.113.10") @Size(max = 64) String destinationIp,
        @Schema(example = "TCP") @NotNull Protocol protocol,
        @Schema(example = "443") @NotBlank @Size(max = 100) String ports,
        @Schema(example = "PERMANENT") @NotNull DurationType durationType,
        @Schema(example = "2026-12-31") LocalDate endDate,
        @Schema(description = "Why the access is needed, in the requester's own words (Arabic or English)",
                example = "Payroll must send the monthly salary file to the bank. Without it salaries are late.")
        @NotBlank @Size(max = 8000) String justification) {

    public enum Protocol { TCP, UDP }

    public enum DurationType { PERMANENT, TEMPORARY }
}
