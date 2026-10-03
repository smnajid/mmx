package com.mmx.order.application.service;

import com.mmx.order.application.ordercreation.OrderCreationCounterparty;
import com.mmx.order.application.port.out.ClientEnablementRepository;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.EffectiveEnablement;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.policy.OrderAgainstInstitutionPolicy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Order-creation counterparties feed new business, so they include only institutions open to new business
 * that hold the counterparty account for the OrderType. For a TradingHub: its active native institutions.
 * For a TradingClient: its open onboarded institutions whose {@link EffectiveEnablement} (active grant ∩ client
 * enablement) includes the currency and term, priced at the linked hub institution's rate. When the linked
 * hub institution is stored in this deployment (same-Organisation client) it must also be open and hold the
 * account; a remote client cannot see the hub's accounts, and the hub enforces them at leg-A accept.
 */
final class OrderCreationDelegatedCounterpartySupport {

    /** A hub rate keyed by hub-native institution code. */
    record RateQuote(String hubInstitutionCode, BigDecimal rate, LocalDate rateDate) {}

    private OrderCreationDelegatedCounterpartySupport() {}

    /** Runs the branch for the LegalEntity's role, or returns {@code unknown} when the LegalEntity does not exist. */
    static <R> R byRole(Optional<LegalEntity> legalEntity, R unknown, Supplier<R> forClient, Supplier<R> forHub) {
        if (legalEntity.isEmpty()) {
            return unknown;
        }
        return legalEntity.get().isTradingClient() ? forClient.get() : forHub.get();
    }

    /** Codes of the active managed currencies that {@code offered} accepts, sorted. */
    static List<String> offeredCurrencies(ManagedCurrencyRepository managedCurrencyRepository, Predicate<ManagedCurrency> offered) {
        return managedCurrencyRepository.findAll().stream()
                .filter(ManagedCurrency::isActive)
                .filter(offered)
                .map(ManagedCurrency::getCode)
                .sorted()
                .toList();
    }

    /**
     * The terms (tenors, or notice periods) a TradingClient may order: some candidate's effective enablement
     * includes the term and its linked hub institution holds one of the hub's quotes for it. Sorted by
     * {@code byCode}.
     */
    static <T> List<T> clientTerms(
            List<ClientCandidate> candidates,
            Function<EffectiveEnablement, Set<T>> termsOf,
            Function<T, List<RateQuote>> hubQuotesFor,
            OrderType orderType,
            InstitutionRepository institutionRepository,
            Comparator<T> byCode) {
        return candidates.stream()
                .flatMap(candidate -> termsOf.apply(candidate.effective()).stream())
                .distinct()
                .filter(
                        term ->
                                anyCandidateHasQuote(
                                        candidates,
                                        effective -> termsOf.apply(effective).contains(term),
                                        hubQuotesFor.apply(term),
                                        orderType,
                                        institutionRepository))
                .sorted(byCode)
                .toList();
    }

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

    /** An onboarded institution of a TradingClient that is open for new business, with its effective enablement. */
    record ClientCandidate(Institution onboarded, String hubInstitutionCode, EffectiveEnablement effective) {}

    /**
     * The client's onboarded institutions open to new business with the OrderType's counterparty account, each
     * with its {@link EffectiveEnablement} for the currency, in onboarding order.
     */
    static List<ClientCandidate> clientCandidates(
            LegalEntityCode clientCode,
            String currency,
            OrderType orderType,
            DelegatedGrantRepository grantRepository,
            InstitutionRepository institutionRepository,
            ClientEnablementRepository clientEnablementRepository) {
        Map<String, DelegatedInstitutionGrant> grantsByHubInstitution =
                grantRepository.findByClientLegalEntityCode(clientCode).stream()
                        .filter(grant -> grant.getCurrency().equals(currency))
                        .collect(Collectors.toMap(DelegatedInstitutionGrant::getHubInstitutionCode, g -> g, (a, b) -> a));
        List<ClientCandidate> candidates = new ArrayList<>();
        for (Institution onboarded : institutionRepository.findOnboardedByLegalEntityCode(clientCode)) {
            if (!isOpenWithAccount(onboarded, orderType)) {
                continue;
            }
            String hubInstitutionCode = onboarded.getHubLink().orElseThrow().hubInstitutionCode();
            EffectiveEnablement effective =
                    EffectiveEnablement.of(
                            Optional.ofNullable(grantsByHubInstitution.get(hubInstitutionCode)),
                            clientEnablementRepository.find(onboarded.getInstitutionCode(), currency));
            candidates.add(new ClientCandidate(onboarded, hubInstitutionCode, effective));
        }
        return candidates;
    }

    /**
     * Whether some candidate permits the term and its linked hub institution holds one of the hub's quotes for
     * it (and, for a same-Organisation client, admits the order type).
     */
    static boolean anyCandidateHasQuote(
            List<ClientCandidate> candidates,
            Predicate<EffectiveEnablement> permitsTerm,
            List<RateQuote> hubQuotes,
            OrderType orderType,
            InstitutionRepository institutionRepository) {
        Set<String> quotedHubInstitutions = hubQuotes.stream().map(RateQuote::hubInstitutionCode).collect(Collectors.toSet());
        return candidates.stream()
                .filter(candidate -> permitsTerm.test(candidate.effective()))
                .filter(candidate -> quotedHubInstitutions.contains(candidate.hubInstitutionCode()))
                .anyMatch(candidate -> linkedHubInstitutionAdmits(candidate.hubInstitutionCode(), orderType, institutionRepository));
    }

    static List<OrderCreationCounterparty> forClient(
            LegalEntityCode clientCode,
            String currency,
            OrderType orderType,
            Predicate<EffectiveEnablement> permitsTerm,
            List<RateQuote> hubQuotes,
            DelegatedGrantRepository grantRepository,
            InstitutionRepository institutionRepository,
            ClientEnablementRepository clientEnablementRepository,
            LocalDate today) {
        Map<String, Institution> onboardedByHub = new HashMap<>();
        for (ClientCandidate candidate :
                clientCandidates(clientCode, currency, orderType, grantRepository, institutionRepository, clientEnablementRepository)) {
            if (permitsTerm.test(candidate.effective())) {
                onboardedByHub.putIfAbsent(candidate.hubInstitutionCode(), candidate.onboarded());
            }
        }

        List<OrderCreationCounterparty> counterparties = new ArrayList<>();
        for (RateQuote quote : hubQuotes) {
            Institution onboarded = onboardedByHub.get(quote.hubInstitutionCode());
            if (onboarded == null || !linkedHubInstitutionAdmits(quote.hubInstitutionCode(), orderType, institutionRepository)) {
                continue;
            }
            counterparties.add(toCounterparty(onboarded, quote, today));
        }
        return sortedByBestRate(counterparties);
    }

    private static boolean linkedHubInstitutionAdmits(
            String hubInstitutionCode, OrderType orderType, InstitutionRepository institutionRepository) {
        Optional<Institution> hubInstitution =
                institutionRepository.findByInstitutionCode(hubInstitutionCode).filter(i -> !i.isOnboarded());
        return hubInstitution.map(institution -> isOpenWithAccount(institution, orderType)).orElse(true);
    }

    /** The options feed new business: a Subscription must be admissible on the institution. */
    private static boolean isOpenWithAccount(Institution institution, OrderType orderType) {
        return OrderAgainstInstitutionPolicy.refusal(institution, OrderOperation.SUBSCRIPTION, orderType).isEmpty();
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
