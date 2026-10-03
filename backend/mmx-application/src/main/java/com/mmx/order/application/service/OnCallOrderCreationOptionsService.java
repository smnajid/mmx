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
import com.mmx.order.application.port.out.ClientEnablementRepository;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.service.OrderCreationDelegatedCounterpartySupport.RateQuote;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
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
    private final ClientEnablementRepository clientEnablementRepository;

    public OnCallOrderCreationOptionsService(
            ManagedCurrencyRepository managedCurrencyRepository,
            OnCallRateRepository onCallRateRepository,
            InstitutionRepository institutionRepository,
            OrderRepository orderRepository,
            LegalEntityRepository legalEntityRepository,
            DelegatedGrantRepository delegatedGrantRepository,
            ClientEnablementRepository clientEnablementRepository) {
        this.managedCurrencyRepository = managedCurrencyRepository;
        this.onCallRateRepository = onCallRateRepository;
        this.institutionRepository = institutionRepository;
        this.orderRepository = orderRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.delegatedGrantRepository = delegatedGrantRepository;
        this.clientEnablementRepository = clientEnablementRepository;
    }

    @Override
    public OnCallCurrenciesResult listCurrencies(LegalEntityCode legalEntityCode) {
        Optional<LegalEntity> legalEntity = legalEntityRepository.findByCode(legalEntityCode);
        if (legalEntity.isEmpty()) {
            return new OnCallCurrenciesResult(List.of());
        }
        List<String> currencies;
        if (legalEntity.get().isTradingClient()) {
            currencies =
                    managedCurrencyRepository.findAll().stream()
                            .filter(ManagedCurrency::isActive)
                            .map(ManagedCurrency::getCode)
                            .filter(currency -> !clientNoticePeriods(legalEntityCode, currency).isEmpty())
                            .sorted()
                            .toList();
        } else {
            Set<String> currenciesWithSegments =
                    new HashSet<>(onCallRateRepository.findDistinctCurrenciesWithOpenOnCallSegments());
            currencies =
                    managedCurrencyRepository.findAll().stream()
                            .filter(ManagedCurrency::isActive)
                            .filter(currency -> !currency.getEnabledNoticePeriods().isEmpty())
                            .map(ManagedCurrency::getCode)
                            .filter(currenciesWithSegments::contains)
                            .sorted()
                            .toList();
        }
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
    public NoticePeriodsResult listNoticePeriods(LegalEntityCode legalEntityCode, String currency) {
        Optional<LegalEntity> legalEntity = legalEntityRepository.findByCode(legalEntityCode);
        if (legalEntity.isEmpty()) {
            return new NoticePeriodsResult(List.of());
        }
        if (legalEntity.get().isTradingClient()) {
            return new NoticePeriodsResult(
                    managedCurrencyRepository
                            .findByCode(currency)
                            .filter(ManagedCurrency::isActive)
                            .map(managed -> clientNoticePeriods(legalEntityCode, managed.getCode()))
                            .orElseGet(List::of));
        }
        return managedCurrencyRepository
                .findByCode(currency)
                .map(this::availableNoticePeriodsForCurrency)
                .orElseGet(() -> new NoticePeriodsResult(List.of()));
    }

    /** Notice periods some open onboarded institution may trade (effective enablement) and the hub has an open segment for. */
    private List<NoticePeriod> clientNoticePeriods(LegalEntityCode clientCode, String currency) {
        List<OrderCreationDelegatedCounterpartySupport.ClientCandidate> candidates =
                OrderCreationDelegatedCounterpartySupport.clientCandidates(
                        clientCode, currency, OrderType.ON_CALL, delegatedGrantRepository, institutionRepository, clientEnablementRepository);
        Set<NoticePeriod> permitted = EnumSet.noneOf(NoticePeriod.class);
        candidates.forEach(candidate -> permitted.addAll(candidate.effective().noticePeriods()));
        List<NoticePeriod> noticePeriods = new ArrayList<>();
        for (NoticePeriod noticePeriod : permitted) {
            List<RateQuote> hubQuotes =
                    onCallRateRepository.findOpenSegmentsByCurrencyAndNoticePeriod(currency, noticePeriod).stream()
                            .map(segment -> new RateQuote(
                                    segment.getCurveKey().institutionCode(), segment.getRate(), segment.getValueDate()))
                            .toList();
            if (OrderCreationDelegatedCounterpartySupport.anyCandidateHasQuote(
                    candidates, effective -> effective.permits(noticePeriod), hubQuotes, OrderType.ON_CALL, institutionRepository)) {
                noticePeriods.add(noticePeriod);
            }
        }
        noticePeriods.sort(Comparator.comparing(NoticePeriod::getCode));
        return noticePeriods;
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
        List<RateQuote> hubQuotes =
                onCallRateRepository.findSegmentsCoveringDate(currency, noticePeriod, valueDate).stream()
                        .map(segment -> new RateQuote(
                                segment.getCurveKey().institutionCode(), segment.getRate(), segment.getValueDate()))
                        .toList();
        if (legalEntity.get().isTradingClient()) {
            return new CounterpartiesResult(
                    OrderCreationDelegatedCounterpartySupport.forClient(
                            legalEntityCode,
                            currency,
                            OrderType.ON_CALL,
                            effective -> effective.permits(noticePeriod),
                            hubQuotes,
                            delegatedGrantRepository,
                            institutionRepository,
                            clientEnablementRepository,
                            LocalDate.now()));
        }
        return new CounterpartiesResult(
                OrderCreationDelegatedCounterpartySupport.forHub(
                        OrderType.ON_CALL, hubQuotes, institutionRepository, LocalDate.now()));
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
