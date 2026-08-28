package com.mmx.order.application.port.out;

/**
 * The {@code RemoteRoutingGateway} circuit is open after sustained leg-A unreachability past the
 * configured threshold. Thrown (not returned) so the calling use case leaves the client-side order in
 * {@code Received} — silence is never terminal; a sustained circuit-open emits an operational signal
 * (alert), never a state transition. When the circuit re-closes after the recovery window, retries
 * resume automatically.
 *
 * <p>Spec: {@code order-routing} — silence is never terminal for a remote client-side order.
 */
public final class RemoteRoutingCircuitOpenException extends RuntimeException {

    public RemoteRoutingCircuitOpenException(String message) {
        super(message);
    }

    public RemoteRoutingCircuitOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
