package com.mmx.order.application.exception;

/** A TradingClient deployment could not read hub reference data live (unreachable, timeout, rejected, non-success). */
public final class HubReferenceDataUnavailableException extends RuntimeException {

    public HubReferenceDataUnavailableException(String message) {
        super(message);
    }

    public HubReferenceDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
