package com.mmx.order.application.port.out;

/**
 * Outcome of resolving a delegated grant for a TradingClient intake term.
 *
 * <ul>
 *   <li>{@link #GRANTED} — an active grant exists and the term is within its enabled set.
 *   <li>{@link #NOT_IN_ENABLED_SET} — an active grant exists but the term is outside its enabled set.
 *   <li>{@link #NO_ACTIVE_GRANT} — no active grant exists for the lookup.
 * </ul>
 */
public enum GrantResolution {
    GRANTED,
    NOT_IN_ENABLED_SET,
    NO_ACTIVE_GRANT;

    public boolean isGranted() {
        return this == GRANTED;
    }
}
