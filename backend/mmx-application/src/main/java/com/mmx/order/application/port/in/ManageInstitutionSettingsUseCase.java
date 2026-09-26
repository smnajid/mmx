package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.List;

public interface ManageInstitutionSettingsUseCase {

    List<Institution> listAll(boolean activeOnly);

    Institution getByCode(String institutionCode);

    /** An institution owned by the active LegalEntity; anything else is not found (counterparty accounts are per LegalEntity). */
    Institution getInScope(ScopeContext scope, String institutionCode);

    /** Native institution onboarding for a TradingHub. */
    Institution onboard(OnboardCommand command);

    /** Trader: deactivate a native institution. ClientRepresentative: offboard an onboarded institution. */
    Institution deactivate(ScopeContext scope, String institutionCode);

    /** Trader: reactivate a native institution. ClientRepresentative: re-onboard (grant required). */
    Institution activate(ScopeContext scope, String institutionCode);

    record OnboardCommand(
            LegalEntityCode owningLegalEntityCode,
            String displayName,
            String termCounterpartyAccount,
            String onCallCounterpartyAccount) {}
}
