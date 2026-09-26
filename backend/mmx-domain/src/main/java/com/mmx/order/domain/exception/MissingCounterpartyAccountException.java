package com.mmx.order.domain.exception;

/** The institution has no counterparty account for the order's OrderType. */
public class MissingCounterpartyAccountException extends InvalidOrderException {

    public MissingCounterpartyAccountException(String message) {
        super(message);
    }
}
