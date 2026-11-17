package com.justify.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One round: what came in, what was still missing, what was asked (D11). */
@Entity
@Table(name = "submission_round")
public class SubmissionRoundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private SubmissionEntity submission;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(name = "input_text", nullable = false)
    private String inputText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_fields", nullable = false, columnDefinition = "jsonb")
    private List<String> missingFields = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> questions = new LinkedHashMap<>();

    @Column(nullable = false)
    private int clarity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SubmissionRoundEntity() {
    }

    public SubmissionRoundEntity(int roundNo, String inputText, List<String> missingFields,
                                 Map<String, String> questions, int clarity, Instant createdAt) {
        this.roundNo = roundNo;
        this.inputText = inputText;
        this.missingFields = new ArrayList<>(missingFields);
        this.questions = new LinkedHashMap<>(questions);
        this.clarity = clarity;
        this.createdAt = createdAt;
    }

    void setSubmission(SubmissionEntity submission) {
        this.submission = submission;
    }

    public Long getId() { return id; }
    public int getRoundNo() { return roundNo; }
    public String getInputText() { return inputText; }
    public List<String> getMissingFields() { return missingFields; }
    public Map<String, String> getQuestions() { return questions; }
    public int getClarity() { return clarity; }
    public Instant getCreatedAt() { return createdAt; }
}
