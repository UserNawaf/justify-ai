package com.justify.core;

/** Lifecycle of a submission. */
public enum Status {
    /** Required information is missing; questions were asked. */
    NEEDS_INFO,
    /** Every required field is present. */
    COMPLETE,
    /** Rounds ran out; rendered anyway with gaps marked "Not provided". */
    INCOMPLETE;

    public boolean isClosed() {
        return this != NEEDS_INFO;
    }
}
