package com.mmx.order.application.service;

import com.mmx.order.application.ordercreation.CounterpartiesResult;
import com.mmx.order.application.ordercreation.OperationsResult;
import com.mmx.order.application.ordercreation.OrderCreationOperation;
import com.mmx.order.application.ordercreation.TermCurrenciesResult;
import com.mmx.order.application.ordercreation.TenorsResult;
import com.mmx.order.application.port.in.ListTermCounterpartiesUseCase;
import com.mmx.order.application.port.in.ListTermCurrenciesUseCase;
import com.mmx.order.application.port.in.ListTermOperationsUseCase;
import com.mmx.order.application.port.in.ListTermTenorsUseCase;
import com.mmx.order.application.port.out.ClientEnablementRepository;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.service.OrderCreationDelegatedCounterpartySupport.RateQuote;
import com.mmx.order.domain.model.EffectiveEnablement;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.Tenor;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TermOrderCreationOptionsService
        implements ListTermCurrenciesUseCase,
                ListTermOperationsUseCase,
                ListTermTenorsUseCase,
                ListTermCounterpartiesUseCase {

    private final ManagedCurrencyRepository managedCurrencyRepository;
    private final TermRateRepository termRateRepository;
    private final InstitutionRepository institutionRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final DelegatedGrantRepository delegatedGrantRepository;
    private final ClientEnablementRepository clientEnablementRepository;
    private final Clock clock;

    public TermOrderCreationOptionsService(
            ManagedCurrencyRepository managedCurrencyRepository,
            TermRateRepository termRateRepository,
            InstitutionRepository institutionRepository,
            LegalEntityRepository legalEntityRepository,
            DelegatedGrantRepository delegatedGrantRepository,
            ClientEnablementRepository clientEnablementRepository,
            Clock clock) {
        this.managedCurrencyRepository = managedCurrencyRepository;
        this.termRateRepository = termRateRepository;
        this.institutionRepository = institutionRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.delegatedGrantRepository = delegatedGrantRepository;
        this.clientEnablementRepository = clientEnablementRepository;
        this.clock = clock;
    }

    @Override
    public TermCurrenciesResult listCurrencies(LegalEntityCode legalEntityCode) {
        List<String> currencies =
                OrderCreationDelegatedCounterpartySupport.byRole(
                        legalEntityRepository.findByCode(legalEntityCode),
                        List.of(),
                        () ->
                                OrderCreationDelegatedCounterpartySupport.offeredCurrencies(
                                        managedCurrencyRepository,
                                        currency -> !clientTenors(legalEntityCode, currency.getCode()).isEmpty()),
                        () -> {
                            Set<String> currenciesWithRates =
                                    new HashSet<>(termRateRepository.findDistinctCurrenciesWithTermRates());
                            return OrderCreationDelegatedCounterpartySupport.offeredCurrencies(
                                    managedCurrencyRepository,
                                    currency ->
                                            !currency.getEnabledTenors().isEmpty()
                                                    && currenciesWithRates.contains(currency.getCode()));
                        });
        return new TermCurrenciesResult(clock.today(), currencies);
    }

    @Override
    public OperationsResult listOperations(String currency) {
        return managedCurrencyRepository
                .findByCode(currency)
                .filter(ManagedCurrency::isActive)
                .map(
                        managed ->
                                new OperationsResult(
                                        List.of(
                                                new OrderCreationOperation(
                                                        OrderOperation.SUBSCRIPTION,
                                                        managed.getMinSubscriptionAmount()))))
                .orElseGet(() -> new OperationsResult(List.of()));
    }

    @Override
    public TenorsResult listTenors(LegalEntityCode legalEntityCode, String currency) {
        return new TenorsResult(
                OrderCreationDelegatedCounterpartySupport.byRole(
                        legalEntityRepository.findByCode(legalEntityCode),
                        List.of(),
                        () ->
                                managedCurrencyRepository
                                        .findByCode(currency)
                                        .filter(ManagedCurrency::isActive)
                                        .map(managed -> clientTenors(legalEntityCode, managed.getCode()))
                                        .orElseGet(List::of),
                        () ->
                                managedCurrencyRepository
                                        .findByCode(currency)
                                        .map(this::hubTenors)
                                        .orElseGet(List::of)));
    }

    /** Tenors some open onboarded institution may trade (effective enablement) and the hub has a rate for. */
    private List<Tenor> clientTenors(LegalEntityCode clientCode, String currency) {
        return OrderCreationDelegatedCounterpartySupport.clientTerms(
                OrderCreationDelegatedCounterpartySupport.clientCandidates(
                        clientCode, currency, OrderType.TERM, delegatedGrantRepository, institutionRepository, clientEnablementRepository),
                EffectiveEnablement::tenors,
                tenor -> hubQuotes(currency, tenor),
                OrderType.TERM,
                institutionRepository,
                Comparator.comparing(Tenor::getCode));
    }

    private List<Tenor> hubTenors(ManagedCurrency managed) {
        return managed.getEnabledTenors().stream()
                .filter(tenor -> !termRateRepository.findLatestRatePerInstitution(managed.getCode(), tenor).isEmpty())
                .sorted(Comparator.comparing(Tenor::getCode))
                .toList();
    }

    @Override
    public CounterpartiesResult listCounterparties(
            LegalEntityCode legalEntityCode, String currency, Tenor tenor) {
        return new CounterpartiesResult(
                OrderCreationDelegatedCounterpartySupport.byRole(
                        legalEntityRepository.findByCode(legalEntityCode),
                        List.of(),
                        () ->
                                OrderCreationDelegatedCounterpartySupport.forClient(
                                        legalEntityCode,
                                        currency,
                                        OrderType.TERM,
                                        effective -> effective.permits(tenor),
                                        hubQuotes(currency, tenor),
                                        delegatedGrantRepository,
                                        institutionRepository,
                                        clientEnablementRepository,
                                        clock.today()),
                        () ->
                                OrderCreationDelegatedCounterpartySupport.forHub(
                                        OrderType.TERM, hubQuotes(currency, tenor), institutionRepository, clock.today())));
    }

    /** The hub's latest rate per institution for the currency and tenor. */
    private List<RateQuote> hubQuotes(String currency, Tenor tenor) {
        return termRateRepository.findLatestRatePerInstitution(currency, tenor).stream()
                .map(row -> new RateQuote(row.institutionCode(), row.rate(), row.tradingDate()))
                .toList();
    }
}
