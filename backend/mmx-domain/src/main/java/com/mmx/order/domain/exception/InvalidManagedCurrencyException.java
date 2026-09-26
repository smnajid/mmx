package com.mmx.order.domain.exception;

public class InvalidManagedCurrencyException extends RuntimeException {

    public InvalidManagedCurrencyException(String message) {
        super(message);
    }
}
