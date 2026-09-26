package com.mmx.order.domain.exception;

public final class DuplicateLegalEntityCodeException extends RuntimeException {

    public DuplicateLegalEntityCodeException(String message) {
        super(message);
    }
}
