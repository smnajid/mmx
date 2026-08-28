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
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retry + circuit-breaker policy for {@link RemoteRoutingGateway} (leg A). The gateway owns indefinite
 * retry with backoff and a circuit-breaker, transparent to the use case: the order stays
 * {@code Received} on transients; a sustained circuit-open emits an operational signal (alert), never a
 * state transition; when the circuit re-closes, retries resume automatically.
 *
 * <p>Spec: {@code order-routing} — silence is never terminal for a remote client-side order. Tested in
 * isolation via fakes (the leg-A outbound orchestrator use case is wired later).
 */
@Tag("fast")
class RemoteRoutingGatewayRetryTest {

    private static final Instant T0 = Instant.parse("2026-05-01T12:00:00Z");
    private static final LegalEntityCode CGD = new LegalEntityCode("CGD");

    @Test
    void transientFailure_thenReachable_retriesWithBackoff_andReturnsAccept() {
        FakeClock clock = new FakeClock(T0);
        RecordingSleeper sleeper = new RecordingSleeper();
        RecordingSignalPort signalPort = new RecordingSignalPort();
        ScriptedDelegate delegate = new ScriptedDelegate();
        delegate.throwTransient("read timeout");
        delegate.throwTransient("503");
        delegate.respondAccept(T0);
        ResilientRemoteRoutingGateway gateway = new ResilientRemoteRoutingGateway(
                delegate, clock, sleeper, signalPort, retryPolicy(3, 3, Duration.ofSeconds(30)));

        RemoteRoutingResponse response = gateway.route(request());

        assertThat(response.isAccepted()).isTrue();
        assertThat(((RemoteRoutingResponse.Accept) response).acceptedAt()).isEqualTo(T0);
        assertThat(delegate.calls).isEqualTo(3);
        assertThat(sleeper.sleeps).hasSize(2);
        assertThat(sleeper.sleeps.get(1)).isGreaterThan(sleeper.sleeps.getFirst());
        assertThat(signalPort.emitted).isEmpty();
    }

    @Test
    void definitiveReject_isReturnedImmediately_notRetried() {
        FakeClock clock = new FakeClock(T0);
        RecordingSleeper sleeper = new RecordingSleeper();
        RecordingSignalPort signalPort = new RecordingSignalPort();
        ScriptedDelegate delegate = new ScriptedDelegate();
        delegate.respondReject("Grant violation: BNP/EUR/6M not enabled");
        ResilientRemoteRoutingGateway gateway = new ResilientRemoteRoutingGateway(
                delegate, clock, sleeper, signalPort, retryPolicy(3, 3, Duration.ofSeconds(30)));

        RemoteRoutingResponse response = gateway.route(request());

        assertThat(response.isRejected()).isTrue();
        assertThat(((RemoteRoutingResponse.Reject) response).reason())
                .isEqualTo("Grant violation: BNP/EUR/6M not enabled");
        assertThat(delegate.calls).isEqualTo(1);
        assertThat(sleeper.sleeps).isEmpty();
        assertThat(signalPort.emitted).isEmpty();
    }

    @Test
    void transientFailure_thenReachable_returnsReject() {
        FakeClock clock = new FakeClock(T0);
        RecordingSleeper sleeper = new RecordingSleeper();
        ScriptedDelegate delegate = new ScriptedDelegate();
        delegate.throwTransient("timeout");
        delegate.respondReject("Currency USD not managed at hub");
        ResilientRemoteRoutingGateway gateway = new ResilientRemoteRoutingGateway(
                delegate, clock, sleeper, new RecordingSignalPort(), retryPolicy(3, 3, Duration.ofSeconds(30)));

        RemoteRoutingResponse response = gateway.route(request());

        assertThat(response.isRejected()).isTrue();
        assertThat(delegate.calls).isEqualTo(2);
        assertThat(sleeper.sleeps).hasSize(1);
    }

    @Test
    void sustainedUnreachability_opensCircuit_emitsSignalOnce_orderStaysReceived() {
        FakeClock clock = new FakeClock(T0);
        RecordingSleeper sleeper = new RecordingSleeper();
        RecordingSignalPort signalPort = new RecordingSignalPort();
        ScriptedDelegate delegate = new ScriptedDelegate();
        delegate.throwTransient("unreachable");
        delegate.throwTransient("unreachable");
        ResilientRemoteRoutingGateway gateway = new ResilientRemoteRoutingGateway(
                delegate, clock, sleeper, signalPort, retryPolicy(2, 2, Duration.ofSeconds(30)));

        assertThatThrownBy(() -> gateway.route(request()))
                .isInstanceOf(RemoteRoutingCircuitOpenException.class);

        assertThat(delegate.calls).isEqualTo(2);
        assertThat(sleeper.sleeps).hasSize(1);
        assertThat(signalPort.emitted).hasSize(1);
        assertThat(signalPort.emitted.getFirst().consecutiveFailures()).isEqualTo(2);
        assertThat(signalPort.emitted.getFirst().openedAt()).isEqualTo(T0);
    }

    @Test
    void openCircuit_fastFailsSubsequentCall_withoutDelegateCallOrReEmit() {
        FakeClock clock = new FakeClock(T0);
        RecordingSignalPort signalPort = new RecordingSignalPort();
        ScriptedDelegate delegate = new ScriptedDelegate();
        delegate.throwTransient("unreachable");
        delegate.throwTransient("unreachable");
        ResilientRemoteRoutingGateway gateway = new ResilientRemoteRoutingGateway(
                delegate, clock, new RecordingSleeper(), signalPort, retryPolicy(2, 2, Duration.ofSeconds(30)));

        assertThatThrownBy(() -> gateway.route(request()))
                .isInstanceOf(RemoteRoutingCircuitOpenException.class);
        int callsAfterFirst = delegate.calls;

        // Still within the recovery window — the circuit stays open.
        assertThatThrownBy(() -> gateway.route(request()))
                .isInstanceOf(RemoteRoutingCircuitOpenException.class);

        assertThat(delegate.calls).isEqualTo(callsAfterFirst);
        assertThat(signalPort.emitted).hasSize(1);
    }

    @Test
    void circuitReclosesAfterRecovery_probeSucceedsAndRetriesResume() {
        FakeClock clock = new FakeClock(T0);
        RecordingSignalPort signalPort = new RecordingSignalPort();
        ScriptedDelegate delegate = new ScriptedDelegate();
        delegate.throwTransient("unreachable");
        delegate.throwTransient("unreachable");
        ResilientRemoteRoutingGateway gateway = new ResilientRemoteRoutingGateway(
                delegate, clock, new RecordingSleeper(), signalPort, retryPolicy(2, 2, Duration.ofSeconds(30)));

        assertThatThrownBy(() -> gateway.route(request())).isInstanceOf(RemoteRoutingCircuitOpenException.class);

        // Advance past the recovery window — the next call is a half-open probe.
        clock.advance(Duration.ofSeconds(31));
        delegate.respondAccept(T0.plusSeconds(31));

        RemoteRoutingResponse response = gateway.route(request());

        assertThat(response.isAccepted()).isTrue();
        assertThat(delegate.calls).isEqualTo(3);

        // Circuit is closed again; a subsequent transient is retried normally (retries resumed).
        delegate.throwTransient("timeout");
        delegate.respondReject("Grant violation");
        RemoteRoutingResponse next = gateway.route(request());
        assertThat(next.isRejected()).isTrue();
    }

    @Test
    void halfOpenProbeFails_reopensCircuit() {
        FakeClock clock = new FakeClock(T0);
        RecordingSignalPort signalPort = new RecordingSignalPort();
        ScriptedDelegate delegate = new ScriptedDelegate();
        delegate.throwTransient("unreachable");
        delegate.throwTransient("unreachable");
        ResilientRemoteRoutingGateway gateway = new ResilientRemoteRoutingGateway(
                delegate, clock, new RecordingSleeper(), signalPort, retryPolicy(2, 2, Duration.ofSeconds(30)));

        assertThatThrownBy(() -> gateway.route(request())).isInstanceOf(RemoteRoutingCircuitOpenException.class);
        int signalCountAfterOpen = signalPort.emitted.size();

        clock.advance(Duration.ofSeconds(31));
        delegate.throwTransient("still down");

        assertThatThrownBy(() -> gateway.route(request()))
                .isInstanceOf(RemoteRoutingCircuitOpenException.class);

        // Re-opened; the circuit stays open for the recovery window again.
        clock.advance(Duration.ofSeconds(5));
        assertThatThrownBy(() -> gateway.route(request()))
                .isInstanceOf(RemoteRoutingCircuitOpenException.class);
        assertThat(signalPort.emitted).hasSize(signalCountAfterOpen + 1);
    }

    private static RemoteRoutingRetryPolicy retryPolicy(
            int maxAttempts, int failureThreshold, Duration recoveryDuration) {
        return new RemoteRoutingRetryPolicy(
                maxAttempts, Duration.ofMillis(10), failureThreshold, recoveryDuration);
    }

    private static RemoteRoutingRequest request() {
        return new RemoteRoutingRequest(
                CGD,
                RoutingId.fromClientOrderId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                new PortfolioNumber("CGD-LOC-001"),
                "BNP",
                new ExternalOrderReference("PM-CLIENT-77"),
                "EUR",
                new BigDecimal("1000000.00"),
                LocalDate.of(2026, 5, 6),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                Tenor._3M,
                null,
                new BigDecimal("3.25"),
                null);
    }

    private static final class FakeClock implements Clock {
        private Instant now;

        FakeClock(Instant initial) {
            this.now = initial;
        }

        void advance(Duration duration) {
            this.now = now.plus(duration);
        }

        @Override
        public Instant now() {
            return now;
        }
    }

    private static final class RecordingSleeper implements Sleeper {
        private final List<Duration> sleeps = new ArrayList<>();

        @Override
        public void sleep(Duration duration) {
            sleeps.add(duration);
        }
    }

    private static final class RecordingSignalPort implements OperationalSignalPort {
        private final List<Emitted> emitted = new ArrayList<>();

        @Override
        public void emitRemoteRoutingCircuitOpen(
                RemoteRoutingRequest request, int consecutiveFailures, Instant openedAt) {
            emitted.add(new Emitted(request, consecutiveFailures, openedAt));
        }

        record Emitted(RemoteRoutingRequest request, int consecutiveFailures, Instant openedAt) {}
    }

    /** Scripted delegate: each enqueued step is either a transient to throw or a response to return. */
    private static final class ScriptedDelegate implements RemoteRoutingGateway {
        private final Deque<Object> script = new ArrayDeque<>();
        private int calls = 0;

        void throwTransient(String message) {
            script.addLast(new RemoteRoutingTransientFailureException(message));
        }

        void respondAccept(Instant at) {
            script.addLast(new RemoteRoutingResponse.Accept(at));
        }

        void respondReject(String reason) {
            script.addLast(new RemoteRoutingResponse.Reject(reason));
        }

        @Override
        public RemoteRoutingResponse route(RemoteRoutingRequest request) {
            calls++;
            Object next = script.pollFirst();
            if (next instanceof RemoteRoutingTransientFailureException failure) {
                throw failure;
            }
            if (next instanceof RemoteRoutingResponse response) {
                return response;
            }
            throw new IllegalStateException("ScriptedDelegate script exhausted");
        }
    }
}
