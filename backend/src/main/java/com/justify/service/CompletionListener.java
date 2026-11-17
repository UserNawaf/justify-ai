package com.justify.service;

/** Called after a submission becomes COMPLETE or INCOMPLETE and the change is committed. */
public interface CompletionListener {

    CompletionListener NONE = snapshot -> { };

    void onClosed(SubmissionSnapshot snapshot);
}
