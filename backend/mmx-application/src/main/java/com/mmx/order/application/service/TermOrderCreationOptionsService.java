package com.mmx.order.application.service;

import com.mmx.order.application.ordercreation.CounterpartiesResult;
import com.mmx.order.application.ordercreation.OperationsResult;
import com.mmx.order.application.ordercreation.OrderCreationCounterparty;
import com.mmx.order.application.ordercreation.OrderCreationOperation;
import com.mmx.order.application.ordercreation.TermCurrenciesResult;
import com.mmx.order.application.ordercreation.TenorsResult;
import com.mmx.order.application.port.in.ListTermCounterpartiesUseCase;
import com.mmx.order.application.port.in.ListTermCurrenciesUseCase;
import com.mmx.order.application.port.in.ListTermOperationsUseCase;
import com.mmx.order.application.port.in.ListTermTenorsUseCase;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.Tenor;

import java.util.ArrayList;
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
    private final Clock clock;

    public TermOrderCreationOptionsService(
            ManagedCurrencyRepository managedCurrencyRepository,
            TermRateRepository termRateRepository,
            InstitutionRepository institutionRepository,
            Clock clock) {
        this.managedCurrencyRepository = managedCurrencyRepository;
        this.termRateRepository = termRateRepository;
        this.institutionRepository = institutionRepository;
        this.clock = clock;
    }

    @Override
    public TermCurrenciesResult listCurrencies() {
        Set<String> currenciesWithRates = new HashSet<>(termRateRepository.findDistinctCurrenciesWithTermRates());
        List<String> currencies =
                managedCurrencyRepository.findAll().stream()
                        .filter(ManagedCurrency::isActive)
                        .filter(currency -> !currency.getEnabledTenors().isEmpty())
                        .map(ManagedCurrency::getCode)
                        .filter(currenciesWithRates::contains)
                        .sorted()
                        .toList();
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
    public TenorsResult listTenors(String currency) {
        return managedCurrencyRepository
                .findByCode(currency)
                .map(this::availableTenorsForCurrency)
                .orElseGet(() -> new TenorsResult(List.of()));
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
    public CounterpartiesResult listCounterparties(String currency, Tenor tenor) {
        List<OrderCreationCounterparty> counterparties =
                termRateRepository.findLatestRatePerInstitution(currency, tenor).stream()
                        .map(row -> toCounterparty(row, clock.today()))
                        .sorted(Comparator.comparing(OrderCreationCounterparty::rate).reversed())
                        .toList();
        return new CounterpartiesResult(counterparties);
    }

    private OrderCreationCounterparty toCounterparty(TermRateAuditRow row, java.time.LocalDate today) {
        String displayName =
                institutionRepository
                        .findByInstitutionCode(row.institutionCode())
                        .map(institution -> institution.getDisplayName())
                        .orElse(row.institutionCode());
        return new OrderCreationCounterparty(
                row.institutionCode(),
                displayName,
                row.rate(),
                row.tradingDate(),
                row.tradingDate().isBefore(today));
    }
}
