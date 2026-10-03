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
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.Tenor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
        Optional<LegalEntity> legalEntity = legalEntityRepository.findByCode(legalEntityCode);
        if (legalEntity.isEmpty()) {
            return new TermCurrenciesResult(clock.today(), List.of());
        }
        List<String> currencies;
        if (legalEntity.get().isTradingClient()) {
            currencies =
                    managedCurrencyRepository.findAll().stream()
                            .filter(ManagedCurrency::isActive)
                            .map(ManagedCurrency::getCode)
                            .filter(currency -> !clientTenors(legalEntityCode, currency).isEmpty())
                            .sorted()
                            .toList();
        } else {
            Set<String> currenciesWithRates = new HashSet<>(termRateRepository.findDistinctCurrenciesWithTermRates());
            currencies =
                    managedCurrencyRepository.findAll().stream()
                            .filter(ManagedCurrency::isActive)
                            .filter(currency -> !currency.getEnabledTenors().isEmpty())
                            .map(ManagedCurrency::getCode)
                            .filter(currenciesWithRates::contains)
                            .sorted()
                            .toList();
        }
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
        Optional<LegalEntity> legalEntity = legalEntityRepository.findByCode(legalEntityCode);
        if (legalEntity.isEmpty()) {
            return new TenorsResult(List.of());
        }
        if (legalEntity.get().isTradingClient()) {
            return new TenorsResult(
                    managedCurrencyRepository
                            .findByCode(currency)
                            .filter(ManagedCurrency::isActive)
                            .map(managed -> clientTenors(legalEntityCode, managed.getCode()))
                            .orElseGet(List::of));
        }
        return managedCurrencyRepository
                .findByCode(currency)
                .map(this::availableTenorsForCurrency)
                .orElseGet(() -> new TenorsResult(List.of()));
    }

    /** Tenors some open onboarded institution may trade (effective enablement) and the hub has a rate for. */
    private List<Tenor> clientTenors(LegalEntityCode clientCode, String currency) {
        List<OrderCreationDelegatedCounterpartySupport.ClientCandidate> candidates =
                OrderCreationDelegatedCounterpartySupport.clientCandidates(
                        clientCode, currency, OrderType.TERM, delegatedGrantRepository, institutionRepository, clientEnablementRepository);
        Set<Tenor> permitted = EnumSet.noneOf(Tenor.class);
        candidates.forEach(candidate -> permitted.addAll(candidate.effective().tenors()));
        List<Tenor> tenors = new ArrayList<>();
        for (Tenor tenor : permitted) {
            List<RateQuote> hubQuotes =
                    termRateRepository.findLatestRatePerInstitution(currency, tenor).stream()
                            .map(row -> new RateQuote(row.institutionCode(), row.rate(), row.tradingDate()))
                            .toList();
            if (OrderCreationDelegatedCounterpartySupport.anyCandidateHasQuote(
                    candidates, effective -> effective.permits(tenor), hubQuotes, OrderType.TERM, institutionRepository)) {
                tenors.add(tenor);
            }
        }
        tenors.sort(Comparator.comparing(Tenor::getCode));
        return tenors;
    }

    private TenorsResult availableTenorsForCurrency(ManagedCurrency managed) {
        List<Tenor> tenors = new ArrayList<>();
        for (Tenor tenor : managed.getEnabledTenors()) {
            if (!termRateRepository.findLatestRatePerInstitution(managed.getCode(), tenor).isEmpty()) {
                tenors.add(tenor);
            }
        }
        tenors.sort(Comparator.comparing(Tenor::getCode));
        return new TenorsResult(tenors);
    }

    @Override
    public CounterpartiesResult listCounterparties(
            LegalEntityCode legalEntityCode, String currency, Tenor tenor) {
        Optional<LegalEntity> legalEntity = legalEntityRepository.findByCode(legalEntityCode);
        if (legalEntity.isEmpty()) {
            return new CounterpartiesResult(List.of());
        }
        List<RateQuote> hubQuotes =
                termRateRepository.findLatestRatePerInstitution(currency, tenor).stream()
                        .map(row -> new RateQuote(row.institutionCode(), row.rate(), row.tradingDate()))
                        .toList();
        if (legalEntity.get().isTradingClient()) {
            return new CounterpartiesResult(
                    OrderCreationDelegatedCounterpartySupport.forClient(
                            legalEntityCode,
                            currency,
                            OrderType.TERM,
                            effective -> effective.permits(tenor),
                            hubQuotes,
                            delegatedGrantRepository,
                            institutionRepository,
                            clientEnablementRepository,
                            clock.today()));
        }
        return new CounterpartiesResult(
                OrderCreationDelegatedCounterpartySupport.forHub(
                        OrderType.TERM, hubQuotes, institutionRepository, clock.today()));
    }
}
