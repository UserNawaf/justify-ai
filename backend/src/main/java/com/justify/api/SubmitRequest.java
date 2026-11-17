package com.justify.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmitRequest(
        @Schema(example = "firewall-rule") @NotBlank @Size(max = 64) String templateId,
        @Schema(example = "Please open port 443 from the payroll server to the bank, urgent")
        @NotBlank @Size(max = 8000) String text,
        @Schema(description = "Calling system, e.g. web, servicenow, jira", example = "web")
        @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]*") String source,
        @Schema(description = "Ticket id in the calling system. Same source + externalRef returns the same submission.",
                example = "RITM0012345")
        @Size(max = 128) String externalRef) {
}
