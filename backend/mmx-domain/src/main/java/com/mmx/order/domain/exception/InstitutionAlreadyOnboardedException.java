package com.mmx.order.domain.exception;

/** The TradingClient already holds an open onboarded institution for that hub institution. */
public class InstitutionAlreadyOnboardedException extends RuntimeException {

    public InstitutionAlreadyOnboardedException(String message) {
        super(message);
    }
}
