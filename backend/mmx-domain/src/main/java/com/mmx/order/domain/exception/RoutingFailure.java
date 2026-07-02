package com.mmx.order.domain.exception;

/** Signals that no global account could be resolved for a routing tuple. */
public final class RoutingFailure extends RuntimeException {

    public RoutingFailure(String message) {
        super(message);
    }
}
