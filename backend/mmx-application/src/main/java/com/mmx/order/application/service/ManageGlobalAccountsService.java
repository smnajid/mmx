package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ManageGlobalAccountsUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.GlobalAccountRepository;
import com.mmx.order.application.port.out.HubScopeResolver;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ReferenceDataMutationGuard;
import com.mmx.order.domain.exception.InvalidLegalEntityException;
import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.List;

public final class ManageGlobalAccountsService implements ManageGlobalAccountsUseCase {

    private final GlobalAccountRepository globalAccountRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final HubScopeResolver hubScopeResolver;
    private final ReferenceDataMutationGuard mutationGuard;

    public ManageGlobalAccountsService(
            GlobalAccountRepository globalAccountRepository,
            LegalEntityRepository legalEntityRepository,
            HubScopeResolver hubScopeResolver,
            ReferenceDataMutationGuard mutationGuard) {
        this.globalAccountRepository = globalAccountRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.hubScopeResolver = hubScopeResolver;
        this.mutationGuard = mutationGuard;
    }

    @Override
    public List<GlobalAccount> listForHub(ScopeContext scope) {
        mutationGuard.ensureTrader(scope);
        LegalEntityCode hub = hubScopeResolver.resolveHubLegalEntityCode(scope);
        return globalAccountRepository.findAllByHub(hub);
    }

    @Override
    public GlobalAccount upsert(UpsertCommand command) {
        mutationGuard.ensureTrader(command.scope());
        LegalEntityCode hub = hubScopeResolver.resolveHubLegalEntityCode(command.scope());
        validateClientConnectedToHub(command.clientLegalEntityCode(), hub);
        if (command.accountRef() == null || command.accountRef().isBlank()) {
            throw new IllegalArgumentException("accountRef must not be blank");
        }
        return globalAccountRepository.save(
                new GlobalAccount(
                        command.clientLegalEntityCode(), hub, command.currency(), command.accountRef().trim()));
    }

    private void validateClientConnectedToHub(LegalEntityCode clientCode, LegalEntityCode hubCode) {
        LegalEntity client =
                legalEntityRepository
                        .findByCode(clientCode)
                        .orElseThrow(
                                () -> new InvalidLegalEntityException("Unknown client LegalEntity: " + clientCode));
        if (!client.isTradingClient()) {
            throw new InvalidLegalEntityException("Global account client must be a TradingClient: " + clientCode);
        }
        if (!hubCode.equals(((com.mmx.order.domain.model.TradingClientRole) client.getRole()).connectedHubCode())) {
            throw new InvalidLegalEntityException(
                    "Client " + clientCode + " is not connected to hub " + hubCode);
        }
    }
}
