package com.mmx.order.application.service;

import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OperationalSignalPort;
import com.mmx.order.application.port.out.RemoteRoutingCircuitOpenException;
import com.mmx.order.application.port.out.RemoteRoutingGateway;
import com.mmx.order.application.port.out.RemoteRoutingRequest;
import com.mmx.order.application.port.out.RemoteRoutingResponse;
import com.mmx.order.application.port.out.RemoteRoutingRetryPolicy;
import com.mmx.order.application.port.out.RemoteRoutingTransientFailureException;
import com.mmx.order.application.port.out.Sleeper;

import java.time.Duration;
import java.time.Instant;

/**
 * CGED leg-A {@link RemoteRoutingGateway} decorator that owns the retry + circuit-breaker policy,
 * transparent to the calling use case (silence is never terminal). The order stays {@code Received}
 * on transients: a definitive {@link RemoteRoutingResponse} (accept or reject) resets the failure
 * counter and returns; a {@link RemoteRoutingTransientFailureException} from the delegate is retried
 * with exponential backoff. On sustained unreachability past {@link RemoteRoutingRetryPolicy
 * #failureThreshold()} the circuit opens — {@link #route(RemoteRoutingRequest)} throws
 * {@link RemoteRoutingCircuitOpenException} (never a state transition) and the operational signal is
 * emitted exactly on the closed→open transition. After {@link RemoteRoutingRetryPolicy
 * #recoveryDuration()} the next call is a half-open probe; on success the circuit closes and retries
 * resume automatically.
 *
 * <p>Retry/backoff/circuit are infrastructure-owned metadata — no domain state. A definitive reject
 * (grant/currency/tenor invalid at the hub) is a business answer and is never retried.
 *
 * <p>Spec: {@code order-routing} — silence is never terminal for a remote client-side order;
 * {@code design.md} D7.
 */
public final class ResilientRemoteRoutingGateway implements RemoteRoutingGateway {

    private final RemoteRoutingGateway delegate;
    private final Clock clock;
    private final Sleeper sleeper;
    private final OperationalSignalPort signalPort;
    private final RemoteRoutingRetryPolicy policy;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private Instant openedAt;

    public ResilientRemoteRoutingGateway(
            RemoteRoutingGateway delegate,
            Clock clock,
            Sleeper sleeper,
            OperationalSignalPort signalPort,
            RemoteRoutingRetryPolicy policy) {
        this.delegate = delegate;
        this.clock = clock;
        this.sleeper = sleeper;
        this.signalPort = signalPort;
        this.policy = policy;
    }

    @Override
    public RemoteRoutingResponse route(RemoteRoutingRequest request) {
        if (state == State.OPEN) {
            if (clock.now().isBefore(openedAt.plus(policy.recoveryDuration()))) {
                throw new RemoteRoutingCircuitOpenException(
                        "Remote routing circuit is open for "
                                + request.originatingLegalEntityCode()
                                + " (sustained leg-A unreachability); client-side order stays RECEIVED");
            }
            // Recovery window elapsed — allow a single half-open probe.
            state = State.HALF_OPEN;
            return halfOpenProbe(request);
        }
        return retryLoop(request);
    }

    private RemoteRoutingResponse halfOpenProbe(RemoteRoutingRequest request) {
        try {
            RemoteRoutingResponse response = delegate.route(request);
            consecutiveFailures = 0;
            state = State.CLOSED;
            return response;
        } catch (RemoteRoutingTransientFailureException failure) {
            consecutiveFailures++;
            openCircuit(request, failure);
            throw new RemoteRoutingCircuitOpenException(
                    "Half-open probe failed for " + request.originatingLegalEntityCode()
                            + "; circuit reopened, order stays RECEIVED",
                    failure);
        }
    }

    private RemoteRoutingResponse retryLoop(RemoteRoutingRequest request) {
        RemoteRoutingTransientFailureException last = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                RemoteRoutingResponse response = delegate.route(request);
                consecutiveFailures = 0;
                return response;
            } catch (RemoteRoutingTransientFailureException failure) {
                last = failure;
                consecutiveFailures++;
                if (attempt < policy.maxAttempts()) {
                    sleeper.sleep(backoffForFailure(attempt));
                }
            }
        }
        if (consecutiveFailures >= policy.failureThreshold()) {
            openCircuit(request, last);
            throw new RemoteRoutingCircuitOpenException(
                    "Remote routing circuit opened for "
                            + request.originatingLegalEntityCode()
                            + " after " + consecutiveFailures
                            + " sustained transient failures; order stays RECEIVED",
                    last);
        }
        throw last;
    }

    private void openCircuit(RemoteRoutingRequest request, RemoteRoutingTransientFailureException cause) {
        state = State.OPEN;
        openedAt = clock.now();
        signalPort.emitRemoteRoutingCircuitOpen(request, consecutiveFailures, openedAt);
    }

    private Duration backoffForFailure(int failureIndex) {
        return policy.initialBackoff().multipliedBy(1L << (failureIndex - 1));
    }

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
