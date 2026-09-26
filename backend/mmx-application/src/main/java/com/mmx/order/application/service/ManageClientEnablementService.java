package com.mmx.order.application.service;

import com.mmx.order.application.exception.InstitutionNotFoundException;
import com.mmx.order.application.port.in.ManageClientEnablementUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.ClientEnablementRepository;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.domain.exception.InvalidInstitutionException;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.ClientEnablement;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.policy.CounterpartyAccountPolicy;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ManageClientEnablementService implements ManageClientEnablementUseCase {

    private final InstitutionRepository institutionRepository;
    private final DelegatedGrantRepository grantRepository;
    private final ClientEnablementRepository enablementRepository;

    public ManageClientEnablementService(
            InstitutionRepository institutionRepository,
            DelegatedGrantRepository grantRepository,
            ClientEnablementRepository enablementRepository) {
        this.institutionRepository = institutionRepository;
        this.grantRepository = grantRepository;
        this.enablementRepository = enablementRepository;
    }

    @Override
    public Institution update(UpdateCommand command) {
        ScopeContext scope = command.scope();
        if (scope == null || scope.role() != Role.CLIENT_REPRESENTATIVE) {
            throw new UnauthorizedUserException("Only a ClientRepresentative can change client enablement");
        }
        String code = Institution.validateCode(command.institutionCode());
        Institution institution =
                institutionRepository
                        .findByInstitutionCode(code)
                        .filter(Institution::isOnboarded)
                        .filter(i -> scope.legalEntityCode().equals(i.getOwningLegalEntityCode()))
                        .orElseThrow(() -> new InstitutionNotFoundException(code));
        HubInstitutionLink hubLink = institution.getHubLink().orElseThrow();

        ClientEnablement current = enablementRepository.find(code, command.currency());
        Optional<DelegatedInstitutionGrant> grant =
                grantRepository.findByKey(
                        new DelegatedGrantKey(hubLink.hubInstitutionCode(), scope.legalEntityCode(), command.currency()));
        ClientEnablement replacement =
                current.replaceWith(command.enabledTenors(), command.enabledNoticePeriods(), grant);
        if (!replacement.tenors().isEmpty()) {
            requireAccount(institution, OrderType.TERM);
        }
        if (!replacement.noticePeriods().isEmpty()) {
            requireAccount(institution, OrderType.ON_CALL);
        }
        enablementRepository.save(scope.legalEntityCode(), replacement);
        return institution;
    }

    @Override
    public List<CurrencyEnablement> enablementsOf(String institutionCode) {
        Optional<Institution> institution =
                institutionRepository.findByInstitutionCode(institutionCode).filter(Institution::isOnboarded);
        if (institution.isEmpty()) {
            return List.of();
        }
        String hubInstitutionCode = institution.get().getHubLink().orElseThrow().hubInstitutionCode();
        Map<String, DelegatedInstitutionGrant> activeGrants =
                grantRepository.findByClientLegalEntityCode(institution.get().getOwningLegalEntityCode()).stream()
                        .filter(g -> g.getHubInstitutionCode().equals(hubInstitutionCode))
                        .filter(DelegatedInstitutionGrant::isActive)
                        .collect(Collectors.toMap(DelegatedInstitutionGrant::getCurrency, Function.identity()));
        Map<String, ClientEnablement> enabled =
                enablementRepository.findByInstitutionCode(institutionCode).stream()
                        .filter(e -> !e.isEmpty())
                        .collect(Collectors.toMap(ClientEnablement::currency, Function.identity()));

        Map<String, CurrencyEnablement> byCurrency = new TreeMap<>();
        for (String currency : union(activeGrants.keySet(), enabled.keySet())) {
            Optional<DelegatedInstitutionGrant> grant = Optional.ofNullable(activeGrants.get(currency));
            ClientEnablement client = enabled.getOrDefault(currency, ClientEnablement.empty(institutionCode, currency));
            byCurrency.put(
                    currency,
                    new CurrencyEnablement(
                            currency,
                            grant.map(DelegatedInstitutionGrant::getEnabledTenors).orElse(EnumSet.noneOf(Tenor.class)),
                            grant.map(DelegatedInstitutionGrant::getEnabledNoticePeriods)
                                    .orElse(EnumSet.noneOf(NoticePeriod.class)),
                            client.tenors(),
                            client.noticePeriods()));
        }
        return List.copyOf(byCurrency.values());
    }

    private static void requireAccount(Institution institution, OrderType orderType) {
        if (institution.getCounterpartyAccounts().accountFor(orderType).isEmpty()) {
            String label = CounterpartyAccountPolicy.label(orderType);
            throw new InvalidInstitutionException(
                    "Institution " + institution.getInstitutionCode() + " has no " + label
                            + " counterparty account; set it before enabling " + label + " business");
        }
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new TreeSet<>(a);
        all.addAll(b);
        return all;
    }
}
