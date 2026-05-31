package com.mmx.order.application.termrate;

import java.util.List;

public class TermRateIngestFailedException extends RuntimeException {

    private final List<TermRateRowError> errors;

    public TermRateIngestFailedException(String message, List<TermRateRowError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<TermRateRowError> getErrors() {
        return errors;
    }
}
