package com.mmx.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec: {@code money-market-order-lifecycle} — rejection origin is classified and persisted. A
 * {@link RejectionOrigin} takes exactly the values {@code TRADER} and {@code ROUTING_FAILURE};
 * every {@link MoneyMarketOrder} reject mutation records the origin alongside the existing
 * free-text {@code rejectionReason}. No additional origin value exists — an unclassified reject
 * is a defect, not a category.
 */
@Tag("fast")
@DisplayName("Rejection origin classification on reject mutations")
class RejectionOriginTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);

    @Test
    void rejectionOrigin_hasExactlyTwoValues_traderAndRoutingFailure() {
        assertThat(Arrays.stream(RejectionOrigin.values()).map(Enum::name))
                .containsExactlyInAnyOrder("TRADER", "ROUTING_FAILURE");
    }

    @Test
    void traderReject_recordsTraderOriginAlongsideReason() {
        MoneyMarketOrder order = receivedDeskOrder();

        order.reject(new TraderId("trader-a"), "No capacity", FIXED_NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo("No capacity");
        assertThat(order.getRejectionOrigin()).isEqualTo(RejectionOrigin.TRADER);
    }

    @Test
    void propagateRejectFromHub_recordsTraderOrigin() {
        // A hub-trader reject propagated to the client-side order is a desk decision — TRADER —
        // whether it arrives synchronously (local pair) or via leg B (remote pair).
        MoneyMarketOrder client = routedClientOrder();

        client.propagateRejectFromHub("Trader declined", FIXED_NOW);

        assertThat(client.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(client.getRejectionReason()).isEqualTo("Trader declined");
        assertThat(client.getRejectionOrigin()).isEqualTo(RejectionOrigin.TRADER);
    }

    @Test
    void rejectAsRoutingFailure_recordsRoutingFailureOrigin() {
        // Intake routing failures (unresolved global account, delegated-grant violation, remote
        // account unresolved, leg-A HTTP reject) reject the client-side order with ROUTING_FAILURE.
        MoneyMarketOrder client = receivedClientOrder();

        client.rejectAsRoutingFailure("No global account configured for routing", FIXED_NOW);

        assertThat(client.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(client.getRejectionReason()).isEqualTo("No global account configured for routing");
        assertThat(client.getRejectionOrigin()).isEqualTo(RejectionOrigin.ROUTING_FAILURE);
    }

    @Test
    void rejectAsRoutingFailure_fromAssignedDeskOrder_isRejectedWithRoutingFailureOrigin() {
        // The routing-failure mutation follows the same desk transition map as reject(): a
        // non-RECEIVED/ASSIGNED state refuses the transition.
        MoneyMarketOrder order = receivedDeskOrder();
        order.assign(new TraderId("trader-a"), FIXED_NOW);

        order.rejectAsRoutingFailure("Grant revoked before routing", FIXED_NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionOrigin()).isEqualTo(RejectionOrigin.ROUTING_FAILURE);
    }

    @Test
    void reconstitute_carriesOrigin_nullReadsAsPreOriginTracking() {
        // Historical rejected rows have no origin — null reads honestly as "pre-origin-tracking".
        MoneyMarketOrder historical = MoneyMarketOrder.reconstitute(
                UUID.randomUUID(),
                new ExternalOrderReference("PM-1"),
                new LegalEntityCode("LOC"),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("LOC-PM-1"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                null,
                Tenor._3M,
                null,
                null,
                "BNP",
                "BNP",
                OrderStatus.REJECTED,
                null,
                null,
                "Pre-tracking reason",
                null,
                null,
                null,
                null,
                null,
                FIXED_NOW.minusSeconds(3600),
                FIXED_NOW);

        assertThat(historical.getRejectionOrigin()).isNull();
        assertThat(historical.getRejectionReason()).isEqualTo("Pre-tracking reason");

        MoneyMarketOrder classified = MoneyMarketOrder.reconstitute(
                UUID.randomUUID(),
                new ExternalOrderReference("PM-2"),
                new LegalEntityCode("LOC"),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("LOC-PM-1"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                null,
                Tenor._3M,
                null,
                null,
                "BNP",
                "BNP",
                OrderStatus.REJECTED,
                null,
                null,
                "Grant violation",
                RejectionOrigin.ROUTING_FAILURE,
                null,
                null,
                null,
                null,
                FIXED_NOW.minusSeconds(3600),
                FIXED_NOW);

        assertThat(classified.getRejectionOrigin()).isEqualTo(RejectionOrigin.ROUTING_FAILURE);
    }

    private static MoneyMarketOrder receivedDeskOrder() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("PM-DESK-" + UUID.randomUUID()),
                new LegalEntityCode("LOC"),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("LOC-PM-1"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                null,
                Tenor._3M,
                null,
                null,
                "BNP",
                "BNP",
                TODAY);
    }

    private static MoneyMarketOrder receivedClientOrder() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("PM-CLIENT-" + UUID.randomUUID()),
                new LegalEntityCode("CGD"),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("CGD-PM-77"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                null,
                Tenor._3M,
                null,
                null,
                "BNP",
                "BNP via LOC",
                TODAY);
    }

    private static MoneyMarketOrder routedClientOrder() {
        MoneyMarketOrder client = receivedClientOrder();
        client.markRouted(RoutingId.fromClientOrderId(client.getId()), FIXED_NOW);
        return client;
    }
}
