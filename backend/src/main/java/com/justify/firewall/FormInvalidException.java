package com.justify.firewall;

import java.util.List;

public class FormInvalidException extends RuntimeException {

    private final List<FirewallFormValidator.Problem> problems;

    public FormInvalidException(List<FirewallFormValidator.Problem> problems) {
        super("form is invalid: " + problems);
        this.problems = List.copyOf(problems);
    }

    public List<FirewallFormValidator.Problem> problems() {
        return problems;
    }
}
