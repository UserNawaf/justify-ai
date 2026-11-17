package com.justify.service;

import java.time.Instant;
import java.util.UUID;

public record Feedback(UUID submissionId, boolean sufficient, String comment, Instant updatedAt) {
}
