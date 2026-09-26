package com.mmx.order.domain.exception;

/** A counterparty account cannot be cleared while client enablement of its OrderType still uses it. */
public class CounterpartyAccountInUseException extends RuntimeException {

    public CounterpartyAccountInUseException(String message) {
        super(message);
    }
}
