package com.mmx.order.application.port.out;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for {@code ResilientRemoteRoutingGateway}'s retry + circuit-breaker policy.
 *
 * <ul>
 *   <li>{@code maxAttempts} — total delegate tries within a single {@code route(...)} call when the
 *       circuit is closed (1 initial call plus retries). A definitive response on any attempt stops
 *       the loop and resets the failure counter.
 *   <li>{@code initialBackoff} — base backoff; the policy backs off exponentially
 *       ({@code initialBackoff * 2^(failureIndex-1)}) between transient failures within a call.
 *   <li>{@code failureThreshold} — consecutive transient failures (counted across attempts) at which
 *       the circuit opens. Sustained unreachability past this threshold trips the circuit and emits an
 *       operational signal.
 *   <li>{@code recoveryDuration} — how long the circuit stays open before a half-open probe is
 *       allowed; on a successful probe the circuit closes and retries resume.
 * </ul>
 *
 * <p>Spec: {@code order-routing} — silence is never terminal; the gateway owns indefinite retry with
 * backoff and a circuit-breaker, transparent to the use case.
 */
public record RemoteRoutingRetryPolicy(
        int maxAttempts, Duration initialBackoff, int failureThreshold, Duration recoveryDuration) {

    public RemoteRoutingRetryPolicy {
        Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
        Objects.requireNonNull(recoveryDuration, "recoveryDuration must not be null");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (initialBackoff.isNegative() || initialBackoff.isZero()) {
            throw new IllegalArgumentException("initialBackoff must be positive");
        }
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be >= 1");
        }
        if (recoveryDuration.isNegative() || recoveryDuration.isZero()) {
            throw new IllegalArgumentException("recoveryDuration must be positive");
        }
    }
}
