package com.justify.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justify.core.Status;
import com.justify.core.Template;
import com.justify.core.pipeline.EvaluationPipeline;
import com.justify.core.pipeline.EvaluationResult;
import com.justify.persistence.SubmissionEntity;
import com.justify.persistence.SubmissionRepository;
import com.justify.persistence.SubmissionRoundEntity;
import com.justify.template.TemplateRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One submission's lifecycle. The model call runs outside any DB transaction (D15); only saving is transactional.
 */
public class JustificationService {

    public static final String DEFAULT_SOURCE = "web";

    private final EvaluationPipeline pipeline;
    private final TemplateRegistry templates;
    private final SubmissionRepository repository;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final Clock clock;

    public JustificationService(EvaluationPipeline pipeline, TemplateRegistry templates,
                                SubmissionRepository repository, TransactionTemplate tx, ObjectMapper mapper,
                                Clock clock) {
        this.pipeline = pipeline;
        this.templates = templates;
        this.repository = repository;
        this.tx = tx;
        this.mapper = mapper;
        this.clock = clock;
    }

    public SubmissionSnapshot submit(String templateId, String text, String source, String externalRef) {
        String src = (source == null || source.isBlank()) ? DEFAULT_SOURCE : source.strip();
        String ref = (externalRef == null || externalRef.isBlank()) ? null : externalRef.strip();

        if (ref != null) {
            Optional<SubmissionSnapshot> existing = findExisting(src, ref);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        Template template = template(templateId);
        EvaluationResult result = pipeline.evaluateText(template, text); // slow, no transaction

        try {
            return tx.execute(status -> {
                Instant now = clock.instant();
                SubmissionEntity entity = new SubmissionEntity(UUID.randomUUID(), template.id(), src, ref, text, now);
                apply(entity, result, text, now);
                return snapshot(repository.saveAndFlush(entity), true);
            });
        } catch (DataIntegrityViolationException e) {
            // Two connector retries raced on the same (source, externalRef): return the winner.
            if (ref != null) {
                return findExisting(src, ref).orElseThrow(() -> e);
            }
            throw e;
        }
    }

    public SubmissionSnapshot answer(UUID id, Map<String, String> answers) {
        try {
            return tx.execute(status -> {
                SubmissionEntity entity = repository.findById(id)
                        .orElseThrow(() -> new NotFoundException("submission " + id + " not found"));
                if (entity.getStatus().isClosed()) {
                    throw new ConflictException("submission is already " + entity.getStatus());
                }
                Template template = template(entity.getTemplateId());
                EvaluationResult result = pipeline.applyAnswers(template, entity.getExtractedFields(),
                        entity.getClarity(), answers, entity.getRoundCount() + 1);
                Instant now = clock.instant();
                apply(entity, result, toJson(answers), now);
                return snapshot(repository.saveAndFlush(entity), false);
            });
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException("submission was updated concurrently, reload and retry");
        }
    }

    public SubmissionSnapshot get(UUID id) {
        return tx.execute(status -> repository.findById(id)
                .map(e -> snapshot(e, false))
                .orElseThrow(() -> new NotFoundException("submission " + id + " not found")));
    }

    public Stats stats() {
        return tx.execute(status -> {
            long total = repository.count();
            long firstPass = repository.countFirstPassComplete();
            return new Stats(total,
                    repository.countByStatus(Status.NEEDS_INFO),
                    repository.countByStatus(Status.COMPLETE),
                    repository.countByStatus(Status.INCOMPLETE),
                    total == 0 ? 0.0 : Math.round(1000.0 * firstPass / total) / 10.0,
                    round2(repository.averageQualityOfClosed()),
                    round2(repository.averageRoundsOfClosed()));
        });
    }

    public Template template(String id) {
        return templates.find(id).orElseThrow(() -> new NotFoundException("template " + id + " not found"));
    }

    private Optional<SubmissionSnapshot> findExisting(String source, String ref) {
        return tx.execute(status -> repository.findBySourceAndExternalRef(source, ref).map(e -> snapshot(e, false)));
    }

    private static void apply(SubmissionEntity entity, EvaluationResult r, String input, Instant now) {
        entity.applyRound(r.status(), r.roundNo(), r.fields(), r.clarity(), r.completeness(), r.qualityScore(),
                r.standardizedText(), now);
        entity.addRound(new SubmissionRoundEntity(r.roundNo(), input, r.missingFields(), r.questions(),
                r.clarity(), now));
    }

    private SubmissionSnapshot snapshot(SubmissionEntity e, boolean created) {
        List<SubmissionRoundEntity> rounds = e.getRounds();
        SubmissionRoundEntity last = rounds.isEmpty() ? null : rounds.get(rounds.size() - 1);
        Map<String, String> questions = e.getStatus() == Status.NEEDS_INFO && last != null
                ? Map.copyOf(last.getQuestions()) : Map.of();
        List<String> missing = last == null ? List.of() : List.copyOf(last.getMissingFields());
        return new SubmissionSnapshot(e.getId(), e.getTemplateId(), e.getSource(), e.getExternalRef(),
                e.getStatus(), e.getRoundCount(), pipeline.maxRounds(), orderedQuestions(e, questions), missing,
                Map.copyOf(e.getExtractedFields()), e.getStandardizedText(), e.getQualityScore().doubleValue(),
                e.getCompleteness().doubleValue(), e.getClarity(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getCompletedAt(), created);
    }

    /** Keeps the template's field order for questions (JSONB does not preserve key order). */
    private Map<String, String> orderedQuestions(SubmissionEntity e, Map<String, String> questions) {
        if (questions.isEmpty()) {
            return questions;
        }
        Map<String, String> ordered = new java.util.LinkedHashMap<>();
        templates.find(e.getTemplateId()).ifPresent(t -> t.fields().forEach(f -> {
            if (questions.containsKey(f.key())) {
                ordered.put(f.key(), questions.get(f.key()));
            }
        }));
        questions.forEach(ordered::putIfAbsent);
        return java.util.Collections.unmodifiableMap(ordered);
    }

    private String toJson(Map<String, String> answers) {
        try {
            return mapper.writeValueAsString(answers);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("answers cannot be serialised", e);
        }
    }

    private static Double round2(Double v) {
        return v == null ? null : Math.round(v * 100) / 100.0;
    }
}
