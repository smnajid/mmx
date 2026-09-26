package com.mmx.order.application.port.out;

import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.domain.exception.InvalidLegalEntityException;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.TradingClientRole;

/**
 * Resolves the LegalEntityCode that reference-data reads should target for a given active scope. A
 * Trader reads their own hub's reference data; a ClientRepresentative reads the connected hub's
 * reference data (in-process, same deployment — ADR-0001).
 */
public final class HubScopeResolver {

    private final LegalEntityRepository legalEntityRepository;

    public HubScopeResolver(LegalEntityRepository legalEntityRepository) {
        this.legalEntityRepository = legalEntityRepository;
    }

    public LegalEntityCode resolveHubLegalEntityCode(ScopeContext scope) {
        if (scope == null) {
            throw new InvalidLegalEntityException("Active scope is required");
        }
        if (scope.role() == Role.TRADER) {
            return scope.legalEntityCode();
        }
        if (scope.role() == Role.CLIENT_REPRESENTATIVE) {
            LegalEntity client =
                    legalEntityRepository
                            .findByCode(scope.legalEntityCode())
                            .orElseThrow(
                                    () ->
                                            new InvalidLegalEntityException(
                                                    "Unknown active LegalEntity: " + scope.legalEntityCode()));
            if (!(client.getRole() instanceof TradingClientRole clientRole)) {
                throw new InvalidLegalEntityException(
                        "A ClientRepresentative must be scoped to a TradingClient: " + scope.legalEntityCode());
            }
            return clientRole.connectedHubCode();
        }
        throw new InvalidLegalEntityException("Unsupported role for reference-data read: " + scope.role());
    }
}
