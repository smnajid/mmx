package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.LegalEntityCode;

import java.util.List;
import java.util.Optional;

/**
 * A ClientRepresentative's granted institutions: hub institutions for which the TradingClient holds at least
 * one active delegated grant (any currency), joined with the hub display names and with the client's own
 * onboarded institutions.
 */
public interface ListGrantedInstitutionsUseCase {

    List<GrantedInstitution> list(ScopeContext scope);

    /**
     * @param displayName the derived "{hub displayName} via {hubLegalEntityCode}"
     * @param currencies currencies with an active grant, sorted
     * @param onboardedInstitutionCode present when the client has onboarded this hub institution
     * @param closedToNewBusiness present with {@code onboardedInstitutionCode}
     */
    record GrantedInstitution(
            LegalEntityCode hubLegalEntityCode,
            String hubInstitutionCode,
            String displayName,
            List<String> currencies,
            Optional<String> onboardedInstitutionCode,
            Optional<Boolean> closedToNewBusiness) {}
}
