package com.mmx.order.application.service;

import com.mmx.order.application.ordercreation.ContractInfoResult;
import com.mmx.order.application.ordercreation.CounterpartiesResult;
import com.mmx.order.application.ordercreation.NoticePeriodsResult;
import com.mmx.order.application.ordercreation.OnCallCurrenciesResult;
import com.mmx.order.application.ordercreation.OperationsResult;
import com.mmx.order.application.ordercreation.OrderCreationCounterparty;
import com.mmx.order.application.ordercreation.OrderCreationOperation;
import com.mmx.order.application.port.out.ExecutedSubscriptionContractInfo;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OnCallRateSegmentStatus;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnCallOrderCreationOptionsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 6);
    private static final LocalDate VALUE_DATE = LocalDate.of(2026, 6, 9);

    @Mock
    ManagedCurrencyRepository managedCurrencyRepository;

    @Mock
    OnCallRateRepository onCallRateRepository;

    @Mock
    InstitutionRepository institutionRepository;

    @Mock
    OrderRepository orderRepository;

    OnCallOrderCreationOptionsService subject;

    @BeforeEach
    void setUp() {
        subject =
                new OnCallOrderCreationOptionsService(
                        managedCurrencyRepository,
                        onCallRateRepository,
                        institutionRepository,
                        orderRepository);
    }

    @Test
    void listCurrencies_filtersByActiveManagedCurrencyEnabledNoticePeriodsAndOpenSegments() {
        ManagedCurrency eur =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.noneOf(Tenor.class),
                        EnumSet.of(NoticePeriod._24H));
        ManagedCurrency usd =
                new ManagedCurrency(
                        "USD",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.noneOf(Tenor.class),
                        EnumSet.of(NoticePeriod._24H));
        when(managedCurrencyRepository.findAll()).thenReturn(List.of(eur, usd));
        when(onCallRateRepository.findDistinctCurrenciesWithOpenOnCallSegments()).thenReturn(List.of("EUR"));

        OnCallCurrenciesResult result = subject.listCurrencies();

        assertThat(result.currencies()).containsExactly("EUR");
    }

    @Test
    void listNoticePeriods_filtersByEnabledNoticePeriodsAndOpenSegmentExistence() {
        ManagedCurrency eur =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.noneOf(Tenor.class),
                        EnumSet.of(NoticePeriod._24H, NoticePeriod._48H));
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));
        when(onCallRateRepository.findOpenSegmentsByCurrencyAndNoticePeriod("EUR", NoticePeriod._24H))
                .thenReturn(List.of(openSegment("BNKCO", NoticePeriod._24H, "2.85")));
        when(onCallRateRepository.findOpenSegmentsByCurrencyAndNoticePeriod("EUR", NoticePeriod._48H))
                .thenReturn(List.of());

        NoticePeriodsResult result = subject.listNoticePeriods("EUR");

        assertThat(result.noticePeriods()).containsExactly(NoticePeriod._24H);
    }

    @Test
    void listCounterparties_returnsSegmentRatesForValueDateSortedByRateDesc() {
        when(onCallRateRepository.findSegmentsCoveringDate("EUR", NoticePeriod._24H, VALUE_DATE))
                .thenReturn(
                        List.of(
                                openSegment("BNKCO", NoticePeriod._24H, "2.90"),
                                openSegment("CDNRD", NoticePeriod._24H, "2.85")));
        when(institutionRepository.findByInstitutionCode("BNKCO"))
                .thenReturn(Optional.of(new Institution("BNKCO", "BankCo", true)));
        when(institutionRepository.findByInstitutionCode("CDNRD"))
                .thenReturn(Optional.of(new Institution("CDNRD", "Canada Rd", true)));

        CounterpartiesResult result = subject.listCounterparties("EUR", NoticePeriod._24H, VALUE_DATE);

        assertThat(result.counterparties())
                .extracting(OrderCreationCounterparty::institutionCode)
                .containsExactly("BNKCO", "CDNRD");
        assertThat(result.counterparties().get(0).rate()).isEqualByComparingTo("2.90");
        assertThat(result.counterparties().get(1).rate()).isEqualByComparingTo("2.85");
    }

    @Test
    void listOperations_returnsAllFourWithCorrectMinAmounts() {
        ManagedCurrency eur =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.noneOf(Tenor.class),
                        EnumSet.of(NoticePeriod._24H));
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

        OperationsResult result = subject.listOperations("EUR");

        assertThat(result.operations())
                .containsExactly(
                        new OrderCreationOperation(OrderOperation.SUBSCRIPTION, new BigDecimal("500000")),
                        new OrderCreationOperation(OrderOperation.INCREASE, new BigDecimal("100000")),
                        new OrderCreationOperation(OrderOperation.DECREASE, new BigDecimal("100000")),
                        new OrderCreationOperation(OrderOperation.REDEMPTION, new BigDecimal("100000")));
    }

    @Test
    void getContractInfo_returnsCurrencyAndNoticePeriodFromExecutedSubscription() {
        when(orderRepository.findExecutedSubscriptionByContractNumber("CT-00042"))
                .thenReturn(Optional.of(new ExecutedSubscriptionContractInfo("EUR", NoticePeriod._24H)));

        ContractInfoResult result = subject.getContractInfo("CT-00042");

        assertThat(result.currency()).isEqualTo("EUR");
        assertThat(result.noticePeriod()).isEqualTo(NoticePeriod._24H);
    }

    @Test
    void getContractInfo_throwsWhenContractNotFound() {
        when(orderRepository.findExecutedSubscriptionByContractNumber("CT-99999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.getContractInfo("CT-99999"))
                .isInstanceOf(OnCallOrderCreationOptionsService.ContractNotFoundException.class);
    }

    private static OnCallRateSegment openSegment(String institutionCode, NoticePeriod noticePeriod, String rate) {
        return new OnCallRateSegment(
                UUID.randomUUID(),
                new OnCallCurveKey(institutionCode, "EUR", noticePeriod),
                new BigDecimal(rate),
                LocalDate.of(2026, 6, 1),
                OnCallRateSegment.NO_END_DATE,
                OnCallRateSegmentStatus.VALID,
                null);
    }
}
