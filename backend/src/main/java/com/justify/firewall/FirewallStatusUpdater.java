package com.justify.firewall;

import com.justify.service.CompletionListener;
import com.justify.service.SubmissionSnapshot;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * When a portal request's justification is finished (by any path, also the plain justification API), the
 * request moves to the engineer's queue. Runs after the justification commit (D23-style listener).
 */
public class FirewallStatusUpdater implements CompletionListener {

    public static final String SOURCE = "firewall-portal";

    private final FirewallRequestRepository repository;
    private final TransactionTemplate tx;
    private final Clock clock;

    public FirewallStatusUpdater(FirewallRequestRepository repository, TransactionTemplate tx, Clock clock) {
        this.repository = repository;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void onClosed(SubmissionSnapshot s) {
        if (!SOURCE.equals(s.source())) {
            return;
        }
        tx.executeWithoutResult(status -> repository.findBySubmissionId(s.id())
                .filter(r -> r.getStatus() == RequestStatus.AWAITING_INFO)
                .ifPresent(r -> r.moveTo(RequestStatus.PENDING_REVIEW, clock.instant())));
    }
}
