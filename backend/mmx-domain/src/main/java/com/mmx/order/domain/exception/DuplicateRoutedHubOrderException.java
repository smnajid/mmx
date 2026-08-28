package com.mmx.order.domain.exception;

/**
 * Signals that a hub-side routed order insert collided with the cross-org idempotency constraint —
 * the partial unique index {@code uq_money_market_order_routing_hub_pair} on
 * {@code (originating_legal_entity_code, routing_id) WHERE originating_legal_entity_code IS NOT NULL}.
 *
 * <p>Spec: {@code order-routing} — cross-boundary correlation and idempotency. A leg-A retry that
 * races past the cheap pre-read is caught here; {@code AcceptRoutedHubOrderService} catches this,
 * resolves to the already-persisted hub-side order, and returns the same accept idempotently. The
 * persistence adapter translates the DB unique-violation into this domain exception (the application
 * layer is framework-free and never sees a {@code DataIntegrityViolationException}).
 */
public final class DuplicateRoutedHubOrderException extends RuntimeException {

    public DuplicateRoutedHubOrderException(String message) {
        super(message);
    }

    public DuplicateRoutedHubOrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
