package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ListGrantedInstitutionsUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.HubInstitutionCatalog;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.TradingClientRole;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ListGrantedInstitutionsService implements ListGrantedInstitutionsUseCase {

    private final DelegatedGrantRepository grantRepository;
    private final HubInstitutionCatalog hubInstitutionCatalog;
    private final InstitutionRepository institutionRepository;
    private final LegalEntityRepository legalEntityRepository;

    public ListGrantedInstitutionsService(
            DelegatedGrantRepository grantRepository,
            HubInstitutionCatalog hubInstitutionCatalog,
            InstitutionRepository institutionRepository,
            LegalEntityRepository legalEntityRepository) {
        this.grantRepository = grantRepository;
        this.hubInstitutionCatalog = hubInstitutionCatalog;
        this.institutionRepository = institutionRepository;
        this.legalEntityRepository = legalEntityRepository;
    }

    @Override
    public List<GrantedInstitution> list(ScopeContext scope) {
        if (scope == null || scope.role() != Role.CLIENT_REPRESENTATIVE) {
            throw new UnauthorizedUserException("Only a ClientRepresentative can list granted institutions");
        }
        LegalEntityCode client = scope.legalEntityCode();
        LegalEntityCode hub = connectedHub(client);

        Map<String, List<String>> activeCurrenciesByHubInstitution =
                grantRepository.findByClientLegalEntityCode(client).stream()
                        .filter(DelegatedInstitutionGrant::isActive)
                        .collect(Collectors.groupingBy(
                                DelegatedInstitutionGrant::getHubInstitutionCode,
                                TreeMap::new,
                                Collectors.collectingAndThen(
                                        Collectors.mapping(DelegatedInstitutionGrant::getCurrency, Collectors.toList()),
                                        currencies -> currencies.stream().sorted().distinct().toList())));
        if (activeCurrenciesByHubInstitution.isEmpty()) {
            return List.of();
        }
        Map<String, Institution> hubNames =
                hubInstitutionCatalog.findAll().stream()
                        .collect(Collectors.toMap(Institution::getInstitutionCode, Function.identity(), (a, b) -> a));

        return activeCurrenciesByHubInstitution.entrySet().stream()
                .map(entry -> {
                    String hubInstitutionCode = entry.getKey();
                    String hubName =
                            Optional.ofNullable(hubNames.get(hubInstitutionCode))
                                    .map(Institution::getDisplayName)
                                    .orElse(hubInstitutionCode);
                    Optional<Institution> onboarded =
                            institutionRepository.findOnboarded(client, new HubInstitutionLink(hub, hubInstitutionCode));
                    return new GrantedInstitution(
                            hub,
                            hubInstitutionCode,
                            Institution.deriveDisplayName(hubName, hub),
                            entry.getValue(),
                            onboarded.map(Institution::getInstitutionCode),
                            onboarded.map(Institution::isClosedToNewBusiness));
                })
                .toList();
    }

    private LegalEntityCode connectedHub(LegalEntityCode client) {
        return legalEntityRepository
                .findByCode(client)
                .map(le -> le.getRole() instanceof TradingClientRole role ? role.connectedHubCode() : null)
                .orElseThrow(() -> new UnauthorizedUserException(
                        "Granted institutions are only available on a TradingClient"));
    }
}
