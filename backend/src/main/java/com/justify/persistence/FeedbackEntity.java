package com.justify.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "submission_feedback")
public class FeedbackEntity {

    @Id
    @Column(name = "submission_id")
    private UUID submissionId;

    @Column(nullable = false)
    private boolean sufficient;

    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeedbackEntity() {
    }

    public FeedbackEntity(UUID submissionId, Instant now) {
        this.submissionId = submissionId;
        this.createdAt = now;
    }

    public void set(boolean sufficient, String comment, Instant now) {
        this.sufficient = sufficient;
        this.comment = comment;
        this.updatedAt = now;
    }

    public UUID getSubmissionId() { return submissionId; }
    public boolean isSufficient() { return sufficient; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
