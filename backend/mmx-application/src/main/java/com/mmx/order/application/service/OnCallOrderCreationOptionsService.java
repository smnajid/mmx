package com.mmx.order.application.service;

import com.mmx.order.application.exception.ContractNotFoundException;
import com.mmx.order.application.ordercreation.ContractInfoResult;
import com.mmx.order.application.ordercreation.CounterpartiesResult;
import com.mmx.order.application.ordercreation.NoticePeriodsResult;
import com.mmx.order.application.ordercreation.OnCallCurrenciesResult;
import com.mmx.order.application.ordercreation.OperationsResult;
import com.mmx.order.application.ordercreation.OrderCreationCounterparty;
import com.mmx.order.application.ordercreation.OrderCreationOperation;
import com.mmx.order.application.port.in.GetContractInfoUseCase;
import com.mmx.order.application.port.in.ListOnCallCounterpartiesUseCase;
import com.mmx.order.application.port.in.ListOnCallCurrenciesUseCase;
import com.mmx.order.application.port.in.ListOnCallNoticePeriodsUseCase;
import com.mmx.order.application.port.in.ListOnCallOperationsUseCase;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.ProxyInstitutionRepository;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OrderOperation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class OnCallOrderCreationOptionsService
        implements ListOnCallCurrenciesUseCase,
                ListOnCallOperationsUseCase,
                ListOnCallNoticePeriodsUseCase,
                ListOnCallCounterpartiesUseCase,
                GetContractInfoUseCase {

    private final ManagedCurrencyRepository managedCurrencyRepository;
    private final OnCallRateRepository onCallRateRepository;
    private final InstitutionRepository institutionRepository;
    private final OrderRepository orderRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final DelegatedGrantRepository delegatedGrantRepository;
    private final ProxyInstitutionRepository proxyInstitutionRepository;

    public OnCallOrderCreationOptionsService(
            ManagedCurrencyRepository managedCurrencyRepository,
            OnCallRateRepository onCallRateRepository,
            InstitutionRepository institutionRepository,
            OrderRepository orderRepository,
            LegalEntityRepository legalEntityRepository,
            DelegatedGrantRepository delegatedGrantRepository,
            ProxyInstitutionRepository proxyInstitutionRepository) {
        this.managedCurrencyRepository = managedCurrencyRepository;
        this.onCallRateRepository = onCallRateRepository;
        this.institutionRepository = institutionRepository;
        this.orderRepository = orderRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.delegatedGrantRepository = delegatedGrantRepository;
        this.proxyInstitutionRepository = proxyInstitutionRepository;
    }

    @Override
    public OnCallCurrenciesResult listCurrencies() {
        Set<String> currenciesWithSegments =
                new HashSet<>(onCallRateRepository.findDistinctCurrenciesWithOpenOnCallSegments());
        List<String> currencies =
                managedCurrencyRepository.findAll().stream()
                        .filter(ManagedCurrency::isActive)
                        .filter(currency -> !currency.getEnabledNoticePeriods().isEmpty())
                        .map(ManagedCurrency::getCode)
                        .filter(currenciesWithSegments::contains)
                        .sorted()
                        .toList();
        return new OnCallCurrenciesResult(currencies);
    }

    @Override
    public OperationsResult listOperations(String currency) {
        return managedCurrencyRepository
                .findByCode(currency)
                .filter(ManagedCurrency::isActive)
                .map(this::operationsForCurrency)
                .orElseGet(() -> new OperationsResult(List.of()));
    }

    private OperationsResult operationsForCurrency(ManagedCurrency managed) {
        return new OperationsResult(
                List.of(
                        new OrderCreationOperation(
                                OrderOperation.SUBSCRIPTION, managed.getMinSubscriptionAmount()),
                        new OrderCreationOperation(
                                OrderOperation.INCREASE, managed.getMinIncreaseDecreaseAmount()),
                        new OrderCreationOperation(
                                OrderOperation.DECREASE, managed.getMinIncreaseDecreaseAmount()),
                        new OrderCreationOperation(
                                OrderOperation.REDEMPTION, managed.getMinIncreaseDecreaseAmount())));
    }

    @Override
    public NoticePeriodsResult listNoticePeriods(String currency) {
        return managedCurrencyRepository
                .findByCode(currency)
                .map(this::availableNoticePeriodsForCurrency)
                .orElseGet(() -> new NoticePeriodsResult(List.of()));
    }

    private NoticePeriodsResult availableNoticePeriodsForCurrency(ManagedCurrency managed) {
        List<NoticePeriod> noticePeriods = new ArrayList<>();
        for (NoticePeriod noticePeriod : managed.getEnabledNoticePeriods()) {
            if (!onCallRateRepository
                    .findOpenSegmentsByCurrencyAndNoticePeriod(managed.getCode(), noticePeriod)
                    .isEmpty()) {
                noticePeriods.add(noticePeriod);
            }
        }
        noticePeriods.sort(Comparator.comparing(NoticePeriod::getCode));
        return new NoticePeriodsResult(noticePeriods);
    }

    @Override
    public CounterpartiesResult listCounterparties(
            LegalEntityCode legalEntityCode,
            String currency,
            NoticePeriod noticePeriod,
            LocalDate valueDate) {
        Optional<LegalEntity> legalEntity = legalEntityRepository.findByCode(legalEntityCode);
        if (legalEntity.isEmpty()) {
            return new CounterpartiesResult(List.of());
        }
        List<OnCallRateSegment> hubSegments =
                onCallRateRepository.findSegmentsCoveringDate(currency, noticePeriod, valueDate);
        if (legalEntity.get().isTradingClient()) {
            return new CounterpartiesResult(
                    OrderCreationDelegatedCounterpartySupport.onCallCounterpartiesForClient(
                            legalEntityCode,
                            currency,
                            noticePeriod,
                            delegatedGrantRepository,
                            proxyInstitutionRepository,
                            hubSegments,
                            LocalDate.now()));
        }
        List<OrderCreationCounterparty> counterparties =
                hubSegments.stream()
                        .map(this::toCounterparty)
                        .sorted(Comparator.comparing(OrderCreationCounterparty::rate).reversed())
                        .toList();
        return new CounterpartiesResult(counterparties);
    }

    private OrderCreationCounterparty toCounterparty(OnCallRateSegment segment) {
        String institutionCode = segment.getCurveKey().institutionCode();
        String displayName =
                institutionRepository
                        .findByInstitutionCode(institutionCode)
                        .map(institution -> institution.getDisplayName())
                        .orElse(institutionCode);
        LocalDate rateDate = segment.getValueDate();
        return new OrderCreationCounterparty(
                institutionCode,
                displayName,
                segment.getRate(),
                rateDate,
                rateDate.isBefore(LocalDate.now()));
    }

    @Override
    public ContractInfoResult getContractInfo(String contractNumber) {
        return orderRepository
                .findExecutedSubscriptionByContractNumber(contractNumber)
                .map(info -> new ContractInfoResult(
                        info.currency(),
                        info.noticePeriod(),
                        info.institutionCode(),
                        info.counterparty()))
                .orElseThrow(() -> new ContractNotFoundException(contractNumber));
    }
}
