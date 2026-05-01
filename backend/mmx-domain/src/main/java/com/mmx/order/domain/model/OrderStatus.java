package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidStatusTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    RECEIVED,
    ASSIGNED,
    EXECUTED,
    CANCELLED,
    REJECTED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            RECEIVED, EnumSet.of(ASSIGNED, CANCELLED, REJECTED),
            ASSIGNED, EnumSet.of(RECEIVED, EXECUTED),
            EXECUTED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            REJECTED, EnumSet.noneOf(OrderStatus.class)
    );

    public OrderStatus transitionTo(OrderStatus target) {
        if (!ALLOWED_TRANSITIONS.get(this).contains(target)) {
            throw new InvalidStatusTransitionException(this, target);
        }
        return target;
    }
}
