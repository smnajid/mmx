package com.mmx.order.application.service;

import com.mmx.order.application.exception.GrantNotFoundException;
import com.mmx.order.application.port.in.ManageDelegatedGrantsUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.domain.exception.DuplicateDelegatedGrantException;
import com.mmx.order.domain.exception.InvalidDelegatedGrantException;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.policy.DelegatedGrantSubsetPolicy;

import java.util.List;

public final class ManageDelegatedGrantsService implements ManageDelegatedGrantsUseCase {

    private final DelegatedGrantRepository grantRepository;
    private final InstitutionRepository institutionRepository;
    private final ManagedCurrencyRepository managedCurrencyRepository;

    public ManageDelegatedGrantsService(
            DelegatedGrantRepository grantRepository,
            InstitutionRepository institutionRepository,
            ManagedCurrencyRepository managedCurrencyRepository) {
        this.grantRepository = grantRepository;
        this.institutionRepository = institutionRepository;
        this.managedCurrencyRepository = managedCurrencyRepository;
    }

    @Override
    public List<DelegatedInstitutionGrant> listGrants(ScopeContext scope) {
        requireTrader(scope);
        return grantRepository.findAll();
    }

    @Override
    public List<DelegatedInstitutionGrant> listClientGrants(ScopeContext scope) {
        requireClientRepresentative(scope);
        return grantRepository.findByClientLegalEntityCode(scope.legalEntityCode());
    }

    @Override
    public DelegatedInstitutionGrant getByKey(DelegatedGrantKey key, ScopeContext scope) {
        requireTrader(scope);
        return grantRepository
                .findByKey(key)
                .orElseThrow(() -> new GrantNotFoundException(key));
    }

    @Override
    public DelegatedInstitutionGrant createGrant(CreateGrantCommand command) {
        ScopeContext scope = command.scope();
        requireTrader(scope);
        DelegatedGrantKey key =
                new DelegatedGrantKey(
                        command.hubInstitutionCode(), command.clientLegalEntityCode(), command.currency());
        if (grantRepository.existsByKey(key)) {
            throw new DuplicateDelegatedGrantException(key);
        }
        return saveValidated(command, true);
    }

    @Override
    public DelegatedInstitutionGrant updateGrant(UpdateGrantCommand command) {
        ScopeContext scope = command.scope();
        requireTrader(scope);
        DelegatedGrantKey key =
                new DelegatedGrantKey(
                        command.hubInstitutionCode(), command.clientLegalEntityCode(), command.currency());
        DelegatedInstitutionGrant existing =
                grantRepository
                        .findByKey(key)
                        .orElseThrow(() -> new GrantNotFoundException(key));
        var tenors =
                command.enabledTenors() != null ? command.enabledTenors() : existing.getEnabledTenors();
        var notices =
                command.enabledNoticePeriods() != null
                        ? command.enabledNoticePeriods()
                        : existing.getEnabledNoticePeriods();
        DelegatedInstitutionGrant updated = existing.withEnabledSets(tenors, notices);
        return grantRepository.save(validated(updated));
    }

    @Override
    public DelegatedInstitutionGrant deactivateGrant(DelegatedGrantKey key, ScopeContext scope) {
        requireTrader(scope);
        DelegatedInstitutionGrant existing =
                grantRepository
                        .findByKey(key)
                        .orElseThrow(() -> new GrantNotFoundException(key));
        return grantRepository.save(existing.withActive(false));
    }

    @Override
    public DelegatedInstitutionGrant reactivateGrant(DelegatedGrantKey key, ScopeContext scope) {
        requireTrader(scope);
        DelegatedInstitutionGrant existing =
                grantRepository
                        .findByKey(key)
                        .orElseThrow(() -> new GrantNotFoundException(key));
        return grantRepository.save(existing.withActive(true));
    }

    private DelegatedInstitutionGrant saveValidated(CreateGrantCommand command, boolean active) {
        DelegatedInstitutionGrant grant =
                new DelegatedInstitutionGrant(
                        command.hubInstitutionCode(),
                        command.clientLegalEntityCode(),
                        command.currency(),
                        command.enabledTenors(),
                        command.enabledNoticePeriods(),
                        active);
        return grantRepository.save(validated(grant));
    }

    private DelegatedInstitutionGrant validated(DelegatedInstitutionGrant grant) {
        Institution hubInstitution =
                institutionRepository
                        .findByInstitutionCode(grant.getHubInstitutionCode())
                        .orElseThrow(
                                () ->
                                        new InvalidDelegatedGrantException(
                                                "Unknown hub institution: " + grant.getHubInstitutionCode()));
        if (!hubInstitution.isActive()) {
            throw new InvalidDelegatedGrantException(
                    "Hub institution " + grant.getHubInstitutionCode() + " is not active");
        }
        ManagedCurrency hubCurrency =
                managedCurrencyRepository
                        .findByCode(grant.getCurrency())
                        .orElseThrow(
                                () ->
                                        new InvalidDelegatedGrantException(
                                                "Unknown managed currency: " + grant.getCurrency()));
        DelegatedGrantSubsetPolicy.validate(
                grant.getEnabledTenors(), grant.getEnabledNoticePeriods(), hubCurrency);
        return grant;
    }

    private static void requireTrader(ScopeContext scope) {
        if (scope == null || scope.role() != Role.TRADER) {
            throw new UnauthorizedUserException(
                    "Delegated institution grants may only be managed by a Trader on the TradingHub");
        }
    }

    private static void requireClientRepresentative(ScopeContext scope) {
        if (scope == null || scope.role() != Role.CLIENT_REPRESENTATIVE) {
            throw new UnauthorizedUserException(
                    "Client-visible grants may only be listed by a ClientRepresentative on a TradingClient");
        }
    }
}
