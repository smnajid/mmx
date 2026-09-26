package com.mmx.order.domain.model;

/** Distinguishes desk-native orders from client-side routed orders for lifecycle transitions. */
public enum OrderLifecycleKind {
    /** Hub-side and native TradingHub desk orders — RECEIVED → ASSIGNED → EXECUTED. */
    DESK,
    /** Client-side routed orders — RECEIVED → ROUTED → EXECUTED; never ASSIGNED. */
    ROUTED_CLIENT
}
