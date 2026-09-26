package com.mmx.order.domain.exception;

/** A Subscription or Increase was refused because the institution is closed to new business. */
public class InstitutionClosedToNewBusinessException extends InvalidOrderException {

    public InstitutionClosedToNewBusinessException(String message) {
        super(message);
    }
}
