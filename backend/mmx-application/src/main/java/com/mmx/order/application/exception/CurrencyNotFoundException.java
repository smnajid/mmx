package com.mmx.order.application.exception;

public final class CurrencyNotFoundException extends RuntimeException {

    public CurrencyNotFoundException(String code) {
        super("Managed currency not found: " + code);
    }
}
