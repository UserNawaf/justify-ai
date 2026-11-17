package com.justify.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justify.core.Language;
import com.justify.core.Status;
import com.justify.core.Template;
import com.justify.core.pipeline.EvaluationPipeline;
import com.justify.core.pipeline.EvaluationResult;
import com.justify.persistence.FeedbackEntity;
import com.justify.persistence.FeedbackRepository;
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
    private final FeedbackRepository feedback;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final CompletionListener listener;

    public JustificationService(EvaluationPipeline pipeline, TemplateRegistry templates,
                                SubmissionRepository repository, FeedbackRepository feedback, TransactionTemplate tx,
                                ObjectMapper mapper, Clock clock, CompletionListener listener) {
        this.pipeline = pipeline;
        this.templates = templates;
        this.repository = repository;
        this.feedback = feedback;
        this.tx = tx;
        this.mapper = mapper;
        this.clock = clock;
        this.listener = listener == null ? CompletionListener.NONE : listener;
    }

    public SubmissionSnapshot submit(String templateId, String text, String source, String externalRef,
                                     Language lang) {
        return submit(templateId, text, Map.of(), source, externalRef, lang);
    }

    /** @param knownFields values the caller already has (confirmed, the model does not override them) */
    public SubmissionSnapshot submit(String templateId, String text, Map<String, String> knownFields, String source,
                                     String externalRef, Language lang) {
        String src = (source == null || source.isBlank()) ? DEFAULT_SOURCE : source.strip();
        String ref = (externalRef == null || externalRef.isBlank()) ? null : externalRef.strip();

        if (ref != null) {
            Optional<SubmissionSnapshot> existing = findExisting(src, ref, lang);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        Template template = template(templateId);
        EvaluationResult result = pipeline.evaluateText(template, text, knownFields); // slow, no transaction

        try {
            return notifyIfClosed(tx.execute(status -> {
                Instant now = clock.instant();
                SubmissionEntity entity = new SubmissionEntity(UUID.randomUUID(), template.id(), src, ref, text, now);
                apply(entity, result, text, now);
                return snapshot(repository.saveAndFlush(entity), true, lang);
            }));
        } catch (DataIntegrityViolationException e) {
            // Two connector retries raced on the same (source, externalRef): return the winner.
            if (ref != null) {
                return findExisting(src, ref, lang).orElseThrow(() -> e);
            }
            throw e;
        }
    }

    public SubmissionSnapshot answer(UUID id, Map<String, String> answers, Language lang) {
        try {
            return notifyIfClosed(tx.execute(status -> {
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
                return snapshot(repository.saveAndFlush(entity), false, lang);
            }));
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException("submission was updated concurrently, reload and retry");
        }
    }

    /** After commit only, so receivers never see a submission that was rolled back. */
    private SubmissionSnapshot notifyIfClosed(SubmissionSnapshot s) {
        if (s.status().isClosed()) {
            try {
                listener.onClosed(s);
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(JustificationService.class)
                        .error("Completion listener failed for {}", s.id(), e);
            }
        }
        return s;
    }

    public SubmissionSnapshot get(UUID id, Language lang) {
        return tx.execute(status -> repository.findById(id)
                .map(e -> snapshot(e, false, lang))
                .orElseThrow(() -> new NotFoundException("submission " + id + " not found")));
    }

    /** The engineer's verdict on a finished justification. Can be changed; the last one counts. */
    public Feedback feedback(UUID id, boolean sufficient, String comment) {
        return tx.execute(status -> {
            SubmissionEntity entity = repository.findById(id)
                    .orElseThrow(() -> new NotFoundException("submission " + id + " not found"));
            if (!entity.getStatus().isClosed()) {
                throw new ConflictException("feedback is only possible once the justification is finished");
            }
            Instant now = clock.instant();
            FeedbackEntity f = feedback.findById(id).orElseGet(() -> new FeedbackEntity(id, now));
            String c = comment == null || comment.isBlank() ? null : comment.strip();
            f.set(sufficient, c, now);
            FeedbackEntity saved = feedback.save(f);
            return new Feedback(id, saved.isSufficient(), saved.getComment(), saved.getUpdatedAt());
        });
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
                    round2(repository.averageRoundsOfClosed()),
                    feedback.count(),
                    feedback.count() == 0 ? null : Math.round(1000.0 * feedback.countBySufficientTrue() / feedback.count()) / 10.0);
        });
    }

    public Template template(String id) {
        return templates.find(id).orElseThrow(() -> new NotFoundException("template " + id + " not found"));
    }

    private Optional<SubmissionSnapshot> findExisting(String source, String ref, Language lang) {
        return tx.execute(status -> repository.findBySourceAndExternalRef(source, ref).map(e -> snapshot(e, false, lang)));
    }

    private static void apply(SubmissionEntity entity, EvaluationResult r, String input, Instant now) {
        entity.applyRound(r.status(), r.roundNo(), r.fields(), r.clarity(), r.completeness(), r.qualityScore(),
                r.standardizedText(), now);
        entity.addRound(new SubmissionRoundEntity(r.roundNo(), input, r.missingFields(), r.questions(),
                r.clarity(), now));
    }

    private SubmissionSnapshot snapshot(SubmissionEntity e, boolean created, Language lang) {
        List<SubmissionRoundEntity> rounds = e.getRounds();
        SubmissionRoundEntity last = rounds.isEmpty() ? null : rounds.get(rounds.size() - 1);
        List<String> missing = last == null ? List.of() : List.copyOf(last.getMissingFields());
        Map<String, String> questions = e.getStatus() == Status.NEEDS_INFO && last != null
                ? localizedQuestions(e.getTemplateId(), last.getQuestions(), lang) : Map.of();
        return new SubmissionSnapshot(e.getId(), e.getTemplateId(), e.getSource(), e.getExternalRef(),
                e.getStatus(), e.getRoundCount(), pipeline.maxRounds(), questions, missing,
                Map.copyOf(e.getExtractedFields()), e.getStandardizedText(), e.getQualityScore().doubleValue(),
                e.getCompleteness().doubleValue(), e.getClarity(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getCompletedAt(), created, e.getRawText());
    }

    /**
     * Questions asked in the last round, in template order and in the caller's language. Stored questions
     * (English) are only a fallback for a template that was removed since.
     */
    private Map<String, String> localizedQuestions(String templateId, Map<String, String> stored, Language lang) {
        Map<String, String> ordered = new java.util.LinkedHashMap<>();
        templates.find(templateId).ifPresent(t -> t.fields().forEach(f -> {
            if (stored.containsKey(f.key())) {
                ordered.put(f.key(), f.question(lang));
            }
        }));
        stored.forEach(ordered::putIfAbsent);
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
