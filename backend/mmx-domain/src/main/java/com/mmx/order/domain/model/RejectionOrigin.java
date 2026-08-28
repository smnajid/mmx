package com.mmx.order.domain.model;

/**
 * Origin of a client-side or desk {@code REJECTED} order, persisted alongside the free-text
 * {@code rejectionReason}.
 *
 * <p>Exactly two values — no {@code SYSTEM}/{@code UNKNOWN} bucket: every reject site knows which
 * kind it is; an unclassified reject is a defect, not a category.
 *
 * <ul>
 *   <li>{@link #ROUTING_FAILURE} — the route itself failed; no hub-side order lifecycle exists or
 *       the route never completed (unresolved global account, delegated-grant/enabled-set
 *       violation at local intake, unresolved external identity account client-side, or a leg-A
 *       HTTP reject closing the client-side order).
 *   <li>{@link #TRADER} — a desk decision rejected an existing order (trader {@code reject(...)}
 *       or a propagated hub-trader reject).
 * </ul>
 *
 * <p>Historical rows rejected before origin tracking read as {@code null} ("pre-origin-tracking").
 */
public enum RejectionOrigin {
    TRADER,
    ROUTING_FAILURE
}
