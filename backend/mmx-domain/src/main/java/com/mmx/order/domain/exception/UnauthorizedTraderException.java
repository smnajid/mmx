package com.mmx.order.domain.exception;

/** Thrown when a Trader attempts an action on an order they are not assigned to. */
public class UnauthorizedTraderException extends RuntimeException {

    public UnauthorizedTraderException(String message) {
        super(message);
    }
}
