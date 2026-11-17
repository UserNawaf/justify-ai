package com.justify.firewall;

/** Lifecycle of a firewall request in the portal. */
public enum RequestStatus {
    /** The justification still has open questions for the requester. */
    AWAITING_INFO,
    /** Justification finished (complete or with gaps); waiting for the network engineer. */
    PENDING_REVIEW,
    APPROVED,
    REJECTED;

    public boolean isDecided() {
        return this == APPROVED || this == REJECTED;
    }
}
