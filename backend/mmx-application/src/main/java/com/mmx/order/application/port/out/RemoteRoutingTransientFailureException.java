package com.mmx.order.application.port.out;

/**
 * Signals a retryable leg-A transient failure (timeout, 5xx, connection reset) from the delegate
 * transport. Distinct from a definitive {@link RemoteRoutingResponse} — a transient failure is NOT
 * surfaced to the use case as a response; {@code ResilientRemoteRoutingGateway} retries it with
 * backoff and, on sustained unreachability, opens the circuit.
 *
 * <p>Spec: {@code order-routing} — silence is never terminal; the gateway owns retry/backoff/
 * circuit-breaker for transients. A definitive {@link RemoteRoutingResponse.Reject} (grant/currency/
 * tenor invalid at the hub) is a business answer and is NOT a transient failure — it is returned as-is.
 */
public final class RemoteRoutingTransientFailureException extends RuntimeException {

    public RemoteRoutingTransientFailureException(String message) {
        super(message);
    }

    public RemoteRoutingTransientFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
