package com.mmx.order.domain.exception;

public class InstitutionSuffixOverflowException extends RuntimeException {

    public InstitutionSuffixOverflowException(String acronymBase) {
        super("Cannot allocate institution code suffix for acronym base: " + acronymBase);
    }
}
