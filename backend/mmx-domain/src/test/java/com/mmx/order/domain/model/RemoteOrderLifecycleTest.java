package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Remote client-side order lifecycle clarifications. Spec: {@code money-market-order-lifecycle}
 * — for a remote client-side order, {@code Received} covers all pre-confirmed-signal conditions
 * (in-flight, circuit-open, awaiting leg-B); {@code Routed} is an acceptable unbounded wait-state;
 * no new {@link OrderStatus} value is introduced; {@code Received→Routed} via leg-A accept OR
 * leg-B {@code ACCEPTED}; propagation transitions are async for remote pairs.
 */
@DisplayName("Remote client-side order lifecycle")
class RemoteOrderLifecycleTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);

    @Test
    void noOrderStatusValue_isIntroducedForRemoteRouting() {
        // Remote routing must NOT introduce Pending / InFlight / Retrying / AwaitingConfirm etc.
        // Received covers all pre-confirmed-signal conditions; Routed is the only post-accept
        // non-terminal; the rest are the existing terminal/Accounted values.
        assertThat(Arrays.stream(OrderStatus.values()).map(Enum::name))
                .containsExactlyInAnyOrder(
                        "RECEIVED", "ROUTED", "ASSIGNED", "EXECUTED", "ACCOUNTED", "CANCELLED", "REJECTED");
    }

    @Test
    void received_coversInFlightCircuitOpenAndAwaitingLegB_noIntermediateStatus() {
        MoneyMarketOrder client = remoteClientOrderInReceived();

        // Leg A in flight, circuit-breaker open, or LODH accepted but leg-B ACCEPTED not yet
        // consumed — all of these are operational metadata; the domain status stays RECEIVED.
        assertThat(client.getStatus()).isEqualTo(OrderStatus.RECEIVED);
    }

    @Test
    void markRouted_closesReceivedToRouted_viaLegAAccept() {
        MoneyMarketOrder client = remoteClientOrderInReceived();
        RoutingId routingId = RoutingId.fromClientOrderId(client.getId());

        client.markRouted(routingId, FIXED_NOW);

        assertThat(client.getStatus()).isEqualTo(OrderStatus.ROUTED);
        assertThat(client.getRoutingId()).isEqualTo(routingId);
    }

    @Test
    void applyAcceptedFromLegB_closesReceivedToRouted_sameTransitionAsLegA() {
        // Leg B is the authoritative lifecycle mirror: it closes Received→Routed using the same
        // domain transition as leg A (no leg-B-specific status value).
        MoneyMarketOrder client = remoteClientOrderInReceived();
        RoutingId routingId = RoutingId.fromClientOrderId(client.getId());

        client.applyAcceptedFromLegB(routingId, FIXED_NOW);

        assertThat(client.getStatus()).isEqualTo(OrderStatus.ROUTED);
        assertThat(client.getRoutingId()).isEqualTo(routingId);
    }

    @Test
    void applyAcceptedFromLegB_isIdempotentWhenLegAAlreadyDelivered_noOpAck() {
        // Spec: silence-is-never-terminal — leg-B ACCEPTED is benign redundancy when leg A
        // delivered first. The apply is a no-op ack (offset advances); no exception.
        MoneyMarketOrder client = remoteClientOrderInReceived();
        RoutingId routingId = RoutingId.fromClientOrderId(client.getId());

        client.markRouted(routingId, FIXED_NOW); // leg A delivered accept
        client.applyAcceptedFromLegB(routingId, FIXED_NOW.plusSeconds(60)); // leg-B ACCEPTED redundant

        assertThat(client.getStatus()).isEqualTo(OrderStatus.ROUTED);
    }

    @Test
    void applyAcceptedFromLegB_onTerminalState_isAnError_notAnAutoTerminalization() {
        // Silence is never terminal: a mismatched terminal outcome cannot be silently overwritten
        // by a stale leg-B ACCEPTED. The use case surfaces this as an error (Kafka poison-message
        // guard) — the domain refuses the transition.
        MoneyMarketOrder client = remoteClientOrderInReceived();
        RoutingId routingId = RoutingId.fromClientOrderId(client.getId());
        client.markRouted(routingId, FIXED_NOW);
        client.propagateCancelFromHub(FIXED_NOW); // terminal — no auto-terminalization on silence

        assertThatThrownBy(() -> client.applyAcceptedFromLegB(routingId, FIXED_NOW.plusSeconds(60)))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(client.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void routed_isAnAcceptableUnboundedWaitState_thenExecutedAsynchronously() {
        // For a remote pair, the client-side order may wait in ROUTED unbounded (leg-B relay lag,
        // hub assignment delay) — ROUTED is a valid wait-state. The async EXECUTED propagation
        // arrives via leg B and applies through the same domain method as the local sync path.
        MoneyMarketOrder client = routedRemoteClientOrder();

        client.propagateExecutionFromHub(
                new ExecutionDetails(
                        new BigDecimal("3.55"),
                        "BNP via LOC",
                        "BNP",
                        FIXED_NOW,
                        new DealingReference("DL-hub"),
                        new ContractNumber("CN-client")),
                "BNP via LOC",
                new ContractNumber("CN-client"),
                FIXED_NOW);

        assertThat(client.getStatus()).isEqualTo(OrderStatus.EXECUTED);
    }

    @Test
    void routed_remoteCancelPropagatesAsynchronously_viaLegB() {
        MoneyMarketOrder client = routedRemoteClientOrder();

        client.propagateCancelFromHub(FIXED_NOW);

        assertThat(client.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void routed_remoteTraderRejectPropagatesAsynchronously_viaLegB() {
        MoneyMarketOrder client = routedRemoteClientOrder();

        client.propagateRejectFromHub("Trader declined", FIXED_NOW);

        assertThat(client.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(client.getRejectionReason()).isEqualTo("Trader declined");
    }

    @Test
    void routed_neverTransitionsToAssigned_aTradingClientHasNoDesk() {
        MoneyMarketOrder client = routedRemoteClientOrder();

        assertThatThrownBy(() -> client.assign(new TraderId("trader-a"), FIXED_NOW))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    private static MoneyMarketOrder remoteClientOrderInReceived() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("PM-CLIENT-" + UUID.randomUUID()),
                new LegalEntityCode("CGD"),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("CGD-PM-77"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                new BigDecimal("3.25"),
                Tenor._3M,
                null,
                null,
                "BNP",
                "BNP via LOC",
                TODAY);
    }

    private static MoneyMarketOrder routedRemoteClientOrder() {
        MoneyMarketOrder client = remoteClientOrderInReceived();
        client.markRouted(RoutingId.fromClientOrderId(client.getId()), FIXED_NOW);
        return client;
    }
}
