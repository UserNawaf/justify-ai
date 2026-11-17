package com.justify.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        @Schema(description = "Was this justification enough to act on without asking the employee?", example = "true")
        @NotNull Boolean sufficient,
        @Schema(example = "Destination IP was wrong") @Size(max = 1000) String comment) {
}
