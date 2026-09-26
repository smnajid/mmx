package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidStatusTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    RECEIVED,
    ROUTED,
    ASSIGNED,
    EXECUTED,
    ACCOUNTED,
    CANCELLED,
    REJECTED;

    private static final Map<OrderStatus, Set<OrderStatus>> DESK_TRANSITIONS = Map.of(
            RECEIVED, EnumSet.of(ASSIGNED, CANCELLED, REJECTED),
            ASSIGNED, EnumSet.of(RECEIVED, EXECUTED, REJECTED),
            EXECUTED, EnumSet.of(ACCOUNTED),
            ACCOUNTED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            REJECTED, EnumSet.noneOf(OrderStatus.class),
            ROUTED, EnumSet.noneOf(OrderStatus.class));

    private static final Map<OrderStatus, Set<OrderStatus>> ROUTED_CLIENT_TRANSITIONS = Map.of(
            RECEIVED, EnumSet.of(ROUTED, CANCELLED, REJECTED),
            ROUTED, EnumSet.of(EXECUTED, REJECTED, CANCELLED),
            EXECUTED, EnumSet.of(ACCOUNTED),
            ACCOUNTED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            REJECTED, EnumSet.noneOf(OrderStatus.class),
            ASSIGNED, EnumSet.noneOf(OrderStatus.class));

    public OrderStatus transitionTo(OrderStatus target) {
        return transitionTo(target, OrderLifecycleKind.DESK);
    }

    public OrderStatus transitionTo(OrderStatus target, OrderLifecycleKind kind) {
        Map<OrderStatus, Set<OrderStatus>> transitions =
                kind == OrderLifecycleKind.ROUTED_CLIENT ? ROUTED_CLIENT_TRANSITIONS : DESK_TRANSITIONS;
        if (!transitions.getOrDefault(this, EnumSet.noneOf(OrderStatus.class)).contains(target)) {
            throw new InvalidStatusTransitionException(this, target);
        }
        return target;
    }
}
