package com.justify.persistence;

import com.justify.core.Status;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "submission")
public class SubmissionEntity {

    @Id
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(nullable = false)
    private String source;

    @Column(name = "external_ref")
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "round_count", nullable = false)
    private int roundCount;

    @Column(name = "quality_score", nullable = false, precision = 3, scale = 1)
    private BigDecimal qualityScore;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal completeness;

    @Column(nullable = false)
    private int clarity;

    @Column(name = "standardized_text")
    private String standardizedText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_fields", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> extractedFields = new LinkedHashMap<>();

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("roundNo asc")
    private List<SubmissionRoundEntity> rounds = new ArrayList<>();

    protected SubmissionEntity() {
    }

    public SubmissionEntity(UUID id, String templateId, String source, String externalRef, String rawText,
                            Instant now) {
        this.id = id;
        this.templateId = templateId;
        this.source = source;
        this.externalRef = externalRef;
        this.rawText = rawText;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Applies the outcome of a round. */
    public void applyRound(Status status, int roundNo, Map<String, String> fields, int clarity, double completeness,
                           double qualityScore, String standardizedText, Instant now) {
        this.status = status;
        this.roundCount = roundNo;
        this.extractedFields = new LinkedHashMap<>(fields);
        this.clarity = clarity;
        this.completeness = BigDecimal.valueOf(completeness).setScale(3, RoundingMode.HALF_UP);
        this.qualityScore = BigDecimal.valueOf(qualityScore).setScale(1, RoundingMode.HALF_UP);
        this.standardizedText = standardizedText;
        this.updatedAt = now;
        if (status.isClosed()) {
            this.completedAt = now;
        }
    }

    public void addRound(SubmissionRoundEntity round) {
        round.setSubmission(this);
        rounds.add(round);
    }

    public UUID getId() { return id; }
    public String getTemplateId() { return templateId; }
    public String getSource() { return source; }
    public String getExternalRef() { return externalRef; }
    public Status getStatus() { return status; }
    public int getRoundCount() { return roundCount; }
    public BigDecimal getQualityScore() { return qualityScore; }
    public BigDecimal getCompleteness() { return completeness; }
    public int getClarity() { return clarity; }
    public String getStandardizedText() { return standardizedText; }
    public Map<String, String> getExtractedFields() { return extractedFields; }
    public String getRawText() { return rawText; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }
    public List<SubmissionRoundEntity> getRounds() { return rounds; }
}
