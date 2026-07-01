package com.mmx.order.application.port.out;

import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.Role;

/**
 * Defence-in-depth guard for hub-owned reference-data mutations (currencies, term rates, OnCall
 * curves). Only a Trader on a TradingHub may mutate; a ClientRepresentative receives an
 * authorisation error. Enforced in the application layer so the invariant holds for any caller.
 */
public final class ReferenceDataMutationGuard {

    public void ensureTrader(ScopeContext scope) {
        if (scope == null || scope.role() != Role.TRADER) {
            throw new UnauthorizedUserException(
                    "Reference-data mutation is Trader-only on the TradingHub");
        }
    }
}
