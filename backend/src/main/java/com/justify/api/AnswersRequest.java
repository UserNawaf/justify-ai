package com.justify.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record AnswersRequest(
        @Schema(example = "{\"duration\": \"Permanent\", \"impact_if_rejected\": \"Salaries are paid late\"}")
        @NotEmpty @Size(max = 50) Map<String, @Size(max = 2000) String> answers) {
}
