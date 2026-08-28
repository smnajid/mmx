package com.mmx.order.application.port.out;

import java.time.Instant;

/**
 * Emits operational signals (alerts) for the cross-org routing transport. A sustained circuit-open
 * emits a {@code circuit-open} signal exactly on the closed→open transition; it is an alert, never an
 * order state transition. Production implementation wires to the ops/observability stack (logs,
 * metrics, on-call alert); tests use a recording fake.
 *
 * <p>Spec: {@code order-routing} — sustained circuit-open SHALL emit an operational signal, never a
 * state transition.
 */
public interface OperationalSignalPort {

    /**
     * Fire when the {@code RemoteRoutingGateway} circuit transitions closed→open after sustained leg-A
     * unreachability past the threshold.
     *
     * @param request the leg-A request that exhausted the retries
     * @param consecutiveFailures the consecutive-transient-failure count that tripped the threshold
     * @param openedAt the instant the circuit opened
     */
    void emitRemoteRoutingCircuitOpen(RemoteRoutingRequest request, int consecutiveFailures, Instant openedAt);
}
