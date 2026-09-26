package com.mmx.order.domain.exception;

/** Routed pair invariant violated (e.g. hub-side order has no linked client-side order). */
public class RoutedOrderPairIntegrityException extends RuntimeException {

    public RoutedOrderPairIntegrityException(String message) {
        super(message);
    }
}
