package com.justify.firewall;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Firewall Access Request form. Technical fields are validated by code; the business justification is
 * checked by AI.
 */
public record FirewallForm(
        // Requested by (from SSO later, D28)
        @Schema(example = "Nawaf Alotaibi") @NotBlank @Size(max = 120) String requesterName,
        @Schema(example = "E10234") @NotBlank @Size(max = 40) String employeeId,
        @Schema(example = "Finance IT") @NotBlank @Size(max = 120) String department,
        @Schema(example = "nawaf@example.com") @Email @Size(max = 200) String email,

        // 1. Request information
        @Schema(example = "NEW_ACCESS") @NotNull RequestType requestType,
        @Schema(example = "MEDIUM") @NotNull Priority priority,
        @Schema(example = "Payroll System") @NotBlank @Size(max = 200) String applicationName,

        // 2. Network configuration
        @Schema(example = "10.10.5.20") @NotBlank @Size(max = 64) String sourceIp,
        @Schema(description = "Optional", example = "payroll-app-01") @Size(max = 255) String sourceHostname,
        @Schema(example = "203.0.113.10") @NotBlank @Size(max = 64) String destinationIp,
        @Schema(description = "Optional hostname or URL", example = "api.bank.example") @Size(max = 255)
        String destinationHost,
        @Schema(example = "443") @NotBlank @Size(max = 100) String ports,
        @Schema(example = "TCP") @NotNull Protocol protocol,
        @Schema(example = "PRODUCTION") @NotNull Environment environment,

        // 3. Business justification
        @Schema(description = "Why the access is needed and the impact if not granted (Arabic or English)",
                example = "Payroll must send the monthly salary file to the bank. Without it salaries are late.")
        @NotBlank @Size(max = 8000) String justification,

        // 4. Additional information
        @Schema(example = "PERMANENT") @NotNull DurationType durationType,
        @Schema(example = "2026-11-20") @NotNull LocalDate startDate,
        @Schema(description = "Required when TEMPORARY", example = "2027-02-20") LocalDate endDate,
        @Schema(example = "Change window: Thursday after 10 pm") @Size(max = 2000) String notes) {

    public enum Protocol { TCP, UDP }

    public enum DurationType { PERMANENT, TEMPORARY }

    public enum RequestType { NEW_ACCESS, MODIFY_ACCESS, REMOVE_ACCESS }

    public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }

    public enum Environment { PRODUCTION, UAT, DEVELOPMENT, DR }
}
