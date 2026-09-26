package com.mmx.order.domain.exception;

public class TermRateIngestException extends RuntimeException {

    private final int line;
    private final String field;

    public TermRateIngestException(int line, String field, String message) {
        super(message);
        this.line = line;
        this.field = field;
    }

    public int getLine() {
        return line;
    }

    public String getField() {
        return field;
    }
}
