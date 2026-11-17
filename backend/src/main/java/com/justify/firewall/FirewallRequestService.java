package com.justify.firewall;

import com.justify.core.Language;
import com.justify.service.ConflictException;
import com.justify.service.JustificationService;
import com.justify.service.NotFoundException;
import com.justify.service.SubmissionSnapshot;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The firewall request portal. Technical fields come from a validated form and are passed to the justification
 * as confirmed values; the AI only has to find the why (purpose, impact) in the requester's own words (D26).
 */
public class FirewallRequestService {

    public static final String TEMPLATE_ID = "firewall-rule";
    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");

    private final JustificationService justifications;
    private final FirewallRequestRepository repository;
    private final FirewallFormValidator validator;
    private final TransactionTemplate tx;
    private final Clock clock;

    public FirewallRequestService(JustificationService justifications, FirewallRequestRepository repository,
                                  FirewallFormValidator validator, TransactionTemplate tx, Clock clock) {
        this.justifications = justifications;
        this.repository = repository;
        this.validator = validator;
        this.tx = tx;
        this.clock = clock;
    }

    public FirewallRequestView create(FirewallForm form, Language lang) {
        List<FirewallFormValidator.Problem> problems = validator.validate(form, LocalDate.now(clock.withZone(RIYADH)));
        if (!problems.isEmpty()) {
            throw new FormInvalidException(problems);
        }
        String ports = FirewallFormValidator.normalisePorts(form.ports());
        String ticketNo = tx.execute(s -> "FW-" + Year.now(clock.withZone(RIYADH)) + "-"
                + String.format("%05d", repository.nextTicketNumber()));

        // Slow model call, outside any transaction (D15). The ticket number makes it idempotent per request.
        SubmissionSnapshot j = justifications.submit(TEMPLATE_ID, form.justification(), knownFields(form, ports),
                FirewallStatusUpdater.SOURCE, ticketNo, lang);

        FirewallRequestEntity saved = tx.execute(s -> repository.save(new FirewallRequestEntity(UUID.randomUUID(),
                ticketNo, form, ports, j.id(),
                j.status().isClosed() ? RequestStatus.PENDING_REVIEW : RequestStatus.AWAITING_INFO,
                clock.instant())));
        return view(saved, j);
    }

    /** Answers to the justification questions, by the requester. */
    public FirewallRequestView answer(UUID id, Map<String, String> answers, Language lang) {
        FirewallRequestEntity r = find(id);
        if (r.getStatus() != RequestStatus.AWAITING_INFO) {
            throw new ConflictException("request " + r.getTicketNo() + " has no open questions");
        }
        justifications.answer(r.getSubmissionId(), answers, lang); // listener moves the request to PENDING_REVIEW
        return get(id, lang);
    }

    /** The network engineer's decision. */
    public FirewallRequestView decide(UUID id, RequestStatus decision, String engineer, String note, Language lang) {
        if (!decision.isDecided()) {
            throw new IllegalArgumentException("decision must be APPROVED or REJECTED");
        }
        if (decision == RequestStatus.REJECTED && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("a rejection needs a note for the requester");
        }
        try {
            tx.executeWithoutResult(s -> {
                FirewallRequestEntity r = repository.findById(id)
                        .orElseThrow(() -> new NotFoundException("request " + id + " not found"));
                if (r.getStatus() != RequestStatus.PENDING_REVIEW) {
                    throw new ConflictException("request " + r.getTicketNo() + " is " + r.getStatus()
                            + ", only PENDING_REVIEW can be decided");
                }
                r.decide(decision, engineer.strip(), note == null || note.isBlank() ? null : note.strip(),
                        clock.instant());
                repository.saveAndFlush(r);
            });
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException("request was updated concurrently, reload and retry");
        }
        return get(id, lang);
    }

    public FirewallRequestView get(UUID id, Language lang) {
        FirewallRequestEntity r = find(id);
        SubmissionSnapshot j = justifications.get(r.getSubmissionId(), lang);
        return view(r, j);
    }

    public FirewallRequestView getByTicket(String ticketNo, Language lang) {
        FirewallRequestEntity r = tx.execute(s -> repository.findByTicketNo(ticketNo.strip().toUpperCase())
                .orElseThrow(() -> new NotFoundException("request " + ticketNo + " not found")));
        return get(r.getId(), lang);
    }

    public Page<FirewallRequestView.Summary> search(RequestStatus status, String employeeId, String q, int page,
                                                    int size) {
        String like = q == null || q.isBlank() ? null : "%" + q.strip().toLowerCase() + "%";
        String emp = employeeId == null || employeeId.isBlank() ? null : employeeId.strip();
        return tx.execute(s -> repository.search(status, emp, like,
                        PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)))
                .map(row -> {
                    FirewallRequestEntity r = (FirewallRequestEntity) row[0];
                    java.math.BigDecimal score = (java.math.BigDecimal) row[1];
                    return new FirewallRequestView.Summary(r.getId(), r.getTicketNo(), r.getStatus(),
                            r.getRequesterName(), r.getDepartment(),
                            r.getSourceHostname() + " (" + r.getSourceIp() + ")",
                            r.getDestinationHost() + (r.getDestinationIp() == null ? "" : " (" + r.getDestinationIp() + ")"),
                            r.getProtocol() + " " + r.getPorts(),
                            score == null ? null : score.doubleValue(), r.getCreatedAt());
                }));
    }

    /** Form values become confirmed justification fields, worded like the template expects. */
    static Map<String, String> knownFields(FirewallForm f, String ports) {
        Map<String, String> known = new LinkedHashMap<>();
        known.put("source", f.sourceHostname().strip() + " (" + f.sourceIp().strip() + ")");
        known.put("destination", f.destinationHost().strip()
                + (f.destinationIp() == null || f.destinationIp().isBlank() ? "" : " (" + f.destinationIp().strip() + ")"));
        known.put("port_protocol", f.protocol() + " " + ports);
        known.put("duration", f.durationType() == FirewallForm.DurationType.PERMANENT
                ? "Permanent" : "Temporary until " + f.endDate());
        return known;
    }

    private FirewallRequestEntity find(UUID id) {
        return tx.execute(s -> repository.findById(id)
                .orElseThrow(() -> new NotFoundException("request " + id + " not found")));
    }

    private static FirewallRequestView view(FirewallRequestEntity r, SubmissionSnapshot j) {
        var justification = new FirewallRequestView.Justification(j.id(), j.status().name(), j.round(), j.maxRounds(),
                j.questions().isEmpty() ? null : j.questions(), j.missingFields(), j.standardizedText(),
                j.qualityScore(), j.rawText());
        return new FirewallRequestView(r.getId(), r.getTicketNo(), r.getStatus(), r.getRequesterName(),
                r.getEmployeeId(), r.getDepartment(), r.getEmail(), r.getSourceHostname(), r.getSourceIp(),
                r.getDestinationHost(), r.getDestinationIp(), r.getProtocol(), r.getPorts(), r.getDurationType(),
                r.getEndDate(), justification, r.getDecidedBy(), r.getDecisionNote(), r.getDecidedAt(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
