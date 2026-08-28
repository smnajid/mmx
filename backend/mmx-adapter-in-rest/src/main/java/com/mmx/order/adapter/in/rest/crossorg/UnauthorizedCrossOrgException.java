package com.mmx.order.adapter.in.rest.crossorg;

/**
 * Thrown when a cross-org transport credential is missing or cannot be resolved to a known legal
 * entity. Mapped to HTTP 401 by {@link CrossOrgExceptionHandler}.
 */
public class UnauthorizedCrossOrgException extends RuntimeException {

    public UnauthorizedCrossOrgException(String message) {
        super(message);
    }
}
