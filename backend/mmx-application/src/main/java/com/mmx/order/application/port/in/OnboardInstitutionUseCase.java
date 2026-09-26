package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.Institution;

/**
 * Role-qualified institution onboarding. A Trader on a TradingHub onboards a native institution by
 * supplying {@code displayName}; a ClientRepresentative on a TradingClient onboards a granted institution
 * ({@code hubInstitutionCode}) and never supplies a free-form {@code displayName}. Onboarding an offboarded
 * institution again re-onboards the same record. Counterparty accounts are optional.
 */
public interface OnboardInstitutionUseCase {

    Result onboard(OnboardCommand command);

    record OnboardCommand(
            ScopeContext scope,
            String displayName,
            String hubInstitutionCode,
            String termCounterpartyAccount,
            String onCallCounterpartyAccount) {

        public OnboardCommand(ScopeContext scope, String displayName, String hubInstitutionCode) {
            this(scope, displayName, hubInstitutionCode, null, null);
        }
    }

    /** {@code created} is false when an offboarded record was re-onboarded. */
    record Result(Institution institution, boolean created) {}
}
