package com.mmx.order.application.port.in;

import com.mmx.order.application.port.in.ScopeContext;

/**
 * Role-qualified institution onboard. A Trader on a TradingHub onboards a native institution by
 * supplying {@code displayName}; a ClientRepresentative on a TradingClient onboards a thin-proxy by
 * selecting an active grant ({@code hubInstitutionCode}). A ClientRepresentative SHALL NOT supply a
 * free-form {@code displayName} for a proxy.
 */
public interface OnboardInstitutionUseCase {

    OnboardedInstitution onboard(OnboardCommand command);

    record OnboardCommand(ScopeContext scope, String displayName, String hubInstitutionCode) {}
}
