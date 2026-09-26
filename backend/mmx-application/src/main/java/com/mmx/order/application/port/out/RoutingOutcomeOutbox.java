package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.MoneyMarketOrder;

import java.time.Instant;

/**
 * Schedules leg-B routed-order outcome outbox rows on the hub deployment (LODH). Each row is
 * committed in the same transaction as the hub-side transition it mirrors, then drained by the
 * existing outbox relay onto the LODH-owned, org-suffixed topic
 * {@code mmx.routed-order-outcome.LODH} (client deployment holds a consume-only ACL).
 *
 * <p>Spec: {@code order-routing} — silence is never terminal; leg B is the authoritative lifecycle
 * mirror. The non-terminal {@code ACCEPTED} outcome is emitted same-tx as hub-side order creation, so
 * the accept signal is exactly as durable as the hub-side order. The cross-boundary correlation key
 * carried on every outcome is {@code (originatingLegalEntityCode, routingId)} (both available on the
 * hub-side order); no internal order UUID crosses the boundary.
 *
 * <p>Adapter lives in {@code mmx-adapter-out-messaging}; terminal outcomes ({@code EXECUTED} /
 * {@code CANCELLED} / {@code REJECTED}) are added when hub-side terminal transitions are wired.
 */
public interface RoutingOutcomeOutbox {

    /**
     * Schedule the leg-B {@code ACCEPTED} outcome, committed in the same transaction as the hub-side
     * order creation.
     *
     * @param hubOrder the newly created hub-side routed order (carries {@code originatingLegalEntityCode}
     *     and {@code routingId}); must be in {@code RECEIVED}
     * @param acceptedAt the accept timestamp (committed on the outbox row)
     */
    void scheduleAccepted(MoneyMarketOrder hubOrder, Instant acceptedAt);

    /**
     * Schedule the leg-B {@code EXECUTED} outcome for a remote pair, committed in the same
     * transaction as the hub-side execute. The hub order carries the hub execution details and the
     * cross-boundary key {@code (originatingLegalEntityCode, routingId)}; no client-side internal id
     * crosses the boundary (the client mints its own deposit contract locally on apply).
     *
     * @param hubOrder the executed hub-side routed order (carries {@code executionDetails},
     *     {@code originatingLegalEntityCode}, {@code routingId})
     * @param executedAt the execution timestamp (committed on the outbox row)
     */
    void scheduleExecuted(MoneyMarketOrder hubOrder, Instant executedAt);

    /**
     * Schedule the leg-B {@code CANCELLED} outcome for a remote pair, committed in the same
     * transaction as the hub-side cancel.
     *
     * @param hubOrder the cancelled hub-side routed order
     * @param cancelledAt the cancel timestamp (committed on the outbox row)
     */
    void scheduleCancelled(MoneyMarketOrder hubOrder, Instant cancelledAt);

    /**
     * Schedule the leg-B {@code REJECTED} outcome for a remote pair, committed in the same
     * transaction as the hub-side trader reject.
     *
     * @param hubOrder the rejected hub-side routed order
     * @param reason the trader rejection reason (carried in the leg-B payload)
     * @param rejectedAt the reject timestamp (committed on the outbox row)
     */
    void scheduleRejected(MoneyMarketOrder hubOrder, String reason, Instant rejectedAt);
}
