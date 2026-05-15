package com.mmx.order.domain.model;

/**
 * Back-office execution handoff delivery state for {@link OrderStatus#EXECUTED} orders.
 * Distinct from lifecycle {@link OrderStatus}.
 */
public enum HandoffStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
