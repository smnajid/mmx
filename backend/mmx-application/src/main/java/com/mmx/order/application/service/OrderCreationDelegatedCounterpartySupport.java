package com.mmx.order.application.service;

import com.mmx.order.application.ordercreation.OrderCreationCounterparty;
import com.mmx.order.application.port.out.ClientEnablementRepository;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.domain.model.ClientEnablement;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.OrderType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Order-creation counterparties feed new business, so they include only institutions open to new business
 * that hold the counterparty account for the OrderType. For a TradingHub: its active native institutions.
 * For a TradingClient: its open onboarded institutions whose effective enablement (active grant ∩ client
 * enablement) includes the currency and term, priced at the linked hub institution's rate. When the linked
 * hub institution is stored in this deployment (same-Organisation client) it must also be open and hold the
 * account; a remote client cannot see the hub's accounts, and the hub enforces them at leg-A accept.
 */
final class OrderCreationDelegatedCounterpartySupport {

    /** A hub rate keyed by hub-native institution code. */
    record RateQuote(String hubInstitutionCode, BigDecimal rate, LocalDate rateDate) {}

    private OrderCreationDelegatedCounterpartySupport() {}

    static List<OrderCreationCounterparty> forHub(
            OrderType orderType, List<RateQuote> quotes, InstitutionRepository institutionRepository, LocalDate today) {
        List<OrderCreationCounterparty> counterparties = new ArrayList<>();
        for (RateQuote quote : quotes) {
            institutionRepository
                    .findByInstitutionCode(quote.hubInstitutionCode())
                    .filter(institution -> isOpenWithAccount(institution, orderType))
                    .ifPresent(institution -> counterparties.add(toCounterparty(institution, quote, today)));
        }
        return sortedByBestRate(counterparties);
    }

    static List<OrderCreationCounterparty> forClient(
            LegalEntityCode clientCode,
            String currency,
            OrderType orderType,
            Predicate<DelegatedInstitutionGrant> grantEnablesTerm,
            Predicate<ClientEnablement> clientEnablesTerm,
            List<RateQuote> hubQuotes,
            DelegatedGrantRepository grantRepository,
            InstitutionRepository institutionRepository,
            ClientEnablementRepository clientEnablementRepository,
            LocalDate today) {
        Set<String> grantedHubInstitutions =
                grantRepository.findByClientLegalEntityCode(clientCode).stream()
                        .filter(DelegatedInstitutionGrant::isActive)
                        .filter(grant -> grant.getCurrency().equals(currency))
                        .filter(grantEnablesTerm)
                        .map(DelegatedInstitutionGrant::getHubInstitutionCode)
                        .collect(Collectors.toSet());
        Map<String, Institution> onboardedByHub = new HashMap<>();
        for (Institution onboarded : institutionRepository.findOnboardedByLegalEntityCode(clientCode)) {
            if (isOpenWithAccount(onboarded, orderType)
                    && clientEnablesTerm.test(
                            clientEnablementRepository.find(onboarded.getInstitutionCode(), currency))) {
                onboardedByHub.putIfAbsent(onboarded.getHubLink().orElseThrow().hubInstitutionCode(), onboarded);
            }
        }

        List<OrderCreationCounterparty> counterparties = new ArrayList<>();
        for (RateQuote quote : hubQuotes) {
            Institution onboarded = onboardedByHub.get(quote.hubInstitutionCode());
            if (onboarded == null
                    || !grantedHubInstitutions.contains(quote.hubInstitutionCode())
                    || !linkedHubInstitutionAdmits(quote.hubInstitutionCode(), orderType, institutionRepository)) {
                continue;
            }
            counterparties.add(toCounterparty(onboarded, quote, today));
        }
        return sortedByBestRate(counterparties);
    }

    private static boolean linkedHubInstitutionAdmits(
            String hubInstitutionCode, OrderType orderType, InstitutionRepository institutionRepository) {
        Optional<Institution> hubInstitution = institutionRepository.findByInstitutionCode(hubInstitutionCode);
        return hubInstitution.map(institution -> isOpenWithAccount(institution, orderType)).orElse(true);
    }

    private static boolean isOpenWithAccount(Institution institution, OrderType orderType) {
        return !institution.isClosedToNewBusiness()
                && institution.getCounterpartyAccounts().accountFor(orderType).isPresent();
    }

    private static OrderCreationCounterparty toCounterparty(Institution institution, RateQuote quote, LocalDate today) {
        return new OrderCreationCounterparty(
                institution.getInstitutionCode(),
                institution.getDisplayName(),
                quote.rate(),
                quote.rateDate(),
                quote.rateDate().isBefore(today));
    }

    private static List<OrderCreationCounterparty> sortedByBestRate(List<OrderCreationCounterparty> counterparties) {
        counterparties.sort(Comparator.comparing(OrderCreationCounterparty::rate).reversed());
        return counterparties;
    }
}
