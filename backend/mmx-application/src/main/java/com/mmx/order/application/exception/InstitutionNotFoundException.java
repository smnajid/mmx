package com.mmx.order.application.exception;

public final class InstitutionNotFoundException extends RuntimeException {

    public InstitutionNotFoundException(String code) {
        super("Institution not found: " + code);
    }
}
