package com.mmx.order.application.service;

import com.mmx.order.application.exception.ContractNotFoundException;
import com.mmx.order.application.ordercreation.ContractInfoResult;
import com.mmx.order.application.ordercreation.CounterpartiesResult;
import com.mmx.order.application.ordercreation.NoticePeriodsResult;
import com.mmx.order.application.ordercreation.OnCallCurrenciesResult;
import com.mmx.order.application.ordercreation.OperationsResult;
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
import com.mmx.order.domain.model.EffectiveEnablement;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
        return new OnCallCurrenciesResult(
                OrderCreationDelegatedCounterpartySupport.byRole(
                        legalEntityRepository.findByCode(legalEntityCode),
                        List.of(),
                        () ->
                                OrderCreationDelegatedCounterpartySupport.offeredCurrencies(
                                        managedCurrencyRepository,
                                        currency -> !clientNoticePeriods(legalEntityCode, currency.getCode()).isEmpty()),
                        () -> {
                            Set<String> currenciesWithSegments =
                                    new HashSet<>(onCallRateRepository.findDistinctCurrenciesWithOpenOnCallSegments());
                            return OrderCreationDelegatedCounterpartySupport.offeredCurrencies(
                                    managedCurrencyRepository,
                                    currency ->
                                            !currency.getEnabledNoticePeriods().isEmpty()
                                                    && currenciesWithSegments.contains(currency.getCode()));
                        }));
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
        return new NoticePeriodsResult(
                OrderCreationDelegatedCounterpartySupport.byRole(
                        legalEntityRepository.findByCode(legalEntityCode),
                        List.of(),
                        () ->
                                managedCurrencyRepository
                                        .findByCode(currency)
                                        .filter(ManagedCurrency::isActive)
                                        .map(managed -> clientNoticePeriods(legalEntityCode, managed.getCode()))
                                        .orElseGet(List::of),
                        () ->
                                managedCurrencyRepository
                                        .findByCode(currency)
                                        .map(this::hubNoticePeriods)
                                        .orElseGet(List::of)));
    }

    /** Notice periods some open onboarded institution may trade (effective enablement) and the hub has an open segment for. */
    private List<NoticePeriod> clientNoticePeriods(LegalEntityCode clientCode, String currency) {
        return OrderCreationDelegatedCounterpartySupport.clientTerms(
                OrderCreationDelegatedCounterpartySupport.clientCandidates(
                        clientCode, currency, OrderType.ON_CALL, delegatedGrantRepository, institutionRepository, clientEnablementRepository),
                EffectiveEnablement::noticePeriods,
                noticePeriod -> quotesOf(onCallRateRepository.findOpenSegmentsByCurrencyAndNoticePeriod(currency, noticePeriod)),
                OrderType.ON_CALL,
                institutionRepository,
                Comparator.comparing(NoticePeriod::getCode));
    }

    private List<NoticePeriod> hubNoticePeriods(ManagedCurrency managed) {
        return managed.getEnabledNoticePeriods().stream()
                .filter(
                        noticePeriod ->
                                !onCallRateRepository
                                        .findOpenSegmentsByCurrencyAndNoticePeriod(managed.getCode(), noticePeriod)
                                        .isEmpty())
                .sorted(Comparator.comparing(NoticePeriod::getCode))
                .toList();
    }

    @Override
    public CounterpartiesResult listCounterparties(
            LegalEntityCode legalEntityCode,
            String currency,
            NoticePeriod noticePeriod,
            LocalDate valueDate) {
        return new CounterpartiesResult(
                OrderCreationDelegatedCounterpartySupport.byRole(
                        legalEntityRepository.findByCode(legalEntityCode),
                        List.of(),
                        () ->
                                OrderCreationDelegatedCounterpartySupport.forClient(
                                        legalEntityCode,
                                        currency,
                                        OrderType.ON_CALL,
                                        effective -> effective.permits(noticePeriod),
                                        coveringQuotes(currency, noticePeriod, valueDate),
                                        delegatedGrantRepository,
                                        institutionRepository,
                                        clientEnablementRepository,
                                        LocalDate.now()),
                        () ->
                                OrderCreationDelegatedCounterpartySupport.forHub(
                                        OrderType.ON_CALL,
                                        coveringQuotes(currency, noticePeriod, valueDate),
                                        institutionRepository,
                                        LocalDate.now())));
    }

    /** The hub's open and pending segments covering the value date, as quotes. */
    private List<RateQuote> coveringQuotes(String currency, NoticePeriod noticePeriod, LocalDate valueDate) {
        return quotesOf(onCallRateRepository.findSegmentsCoveringDate(currency, noticePeriod, valueDate));
    }

    private static List<RateQuote> quotesOf(List<OnCallRateSegment> segments) {
        return segments.stream()
                .map(segment -> new RateQuote(segment.getCurveKey().institutionCode(), segment.getRate(), segment.getValueDate()))
                .toList();
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
