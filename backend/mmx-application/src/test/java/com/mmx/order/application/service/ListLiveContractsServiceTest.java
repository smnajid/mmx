package com.mmx.order.application.service;

import com.mmx.order.application.ordercreation.LiveContractResult;
import com.mmx.order.application.ordercreation.LiveContractsResult;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.ExecutedSubscriptionContract;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
@Tag("fast")

@ExtendWith(MockitoExtension.class)
class ListLiveContractsServiceTest {

    private static final String PORTFOLIO = "PF-001";
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 13);
    private static final LocalDate VALUE_DATE = LocalDate.of(2026, 6, 1);

    @Mock
    OrderRepository orderRepository;

    @Mock
    Clock clock;

    ListLiveContractsService subject;

    @BeforeEach
    void setUp() {
        when(clock.today()).thenReturn(TODAY);
        subject = new ListLiveContractsService(orderRepository, clock);
    }

    @Test
    void onCall_returnsLiveContractWhenNoRedemption() {
        when(orderRepository.findExecutedSubscriptionsByPortfolioAndOrderType(PORTFOLIO, OrderType.ON_CALL))
                .thenReturn(
                        List.of(
                                new ExecutedSubscriptionContract(
                                        "CT-00042",
                                        OrderType.ON_CALL,
                                        "EUR",
                                        NoticePeriod._24H,
                                        null,
                                        VALUE_DATE,
                                        new BigDecimal("5000000.00"))));
        when(orderRepository.findContractNumbersWithNonCancelledRedemption(List.of("CT-00042")))
                .thenReturn(Set.of());

        LiveContractsResult result = subject.listLiveContracts(PORTFOLIO, OrderType.ON_CALL);

        assertThat(result.contracts())
                .containsExactly(
                        new LiveContractResult(
                                "CT-00042",
                                OrderType.ON_CALL,
                                "EUR",
                                NoticePeriod._24H,
                                null,
                                VALUE_DATE,
                                null,
                                new BigDecimal("5000000.00")));
    }

    @Test
    void onCall_excludesContractWithNonCancelledRedemption() {
        when(orderRepository.findExecutedSubscriptionsByPortfolioAndOrderType(PORTFOLIO, OrderType.ON_CALL))
                .thenReturn(
                        List.of(
                                new ExecutedSubscriptionContract(
                                        "CT-00042",
                                        OrderType.ON_CALL,
                                        "EUR",
                                        NoticePeriod._24H,
                                        null,
                                        VALUE_DATE,
                                        new BigDecimal("5000000.00"))));
        when(orderRepository.findContractNumbersWithNonCancelledRedemption(List.of("CT-00042")))
                .thenReturn(Set.of("CT-00042"));

        LiveContractsResult result = subject.listLiveContracts(PORTFOLIO, OrderType.ON_CALL);

        assertThat(result.contracts()).isEmpty();
    }

    @Test
    void term_returnsLiveContractWhenEndDateIsInFuture() {
        when(orderRepository.findExecutedSubscriptionsByPortfolioAndOrderType(PORTFOLIO, OrderType.TERM))
                .thenReturn(
                        List.of(
                                new ExecutedSubscriptionContract(
                                        "CT-00100",
                                        OrderType.TERM,
                                        "EUR",
                                        null,
                                        Tenor._3M,
                                        VALUE_DATE,
                                        new BigDecimal("10000000.00"))));
        when(orderRepository.findContractNumbersWithNonCancelledRedemption(List.of("CT-00100")))
                .thenReturn(Set.of());

        LiveContractsResult result = subject.listLiveContracts(PORTFOLIO, OrderType.TERM);

        assertThat(result.contracts())
                .containsExactly(
                        new LiveContractResult(
                                "CT-00100",
                                OrderType.TERM,
                                "EUR",
                                null,
                                Tenor._3M,
                                VALUE_DATE,
                                LocalDate.of(2026, 9, 1),
                                new BigDecimal("10000000.00")));
    }

    @Test
    void term_excludesMaturedContract() {
        when(orderRepository.findExecutedSubscriptionsByPortfolioAndOrderType(PORTFOLIO, OrderType.TERM))
                .thenReturn(
                        List.of(
                                new ExecutedSubscriptionContract(
                                        "CT-00099",
                                        OrderType.TERM,
                                        "EUR",
                                        null,
                                        Tenor._3M,
                                        LocalDate.of(2026, 3, 1),
                                        new BigDecimal("10000000.00"))));
        when(orderRepository.findContractNumbersWithNonCancelledRedemption(List.of("CT-00099")))
                .thenReturn(Set.of());

        LiveContractsResult result = subject.listLiveContracts(PORTFOLIO, OrderType.TERM);

        assertThat(result.contracts()).isEmpty();
    }

    @Test
    void filtersMixedCandidatesToLiveContractsOnly() {
        when(orderRepository.findExecutedSubscriptionsByPortfolioAndOrderType(PORTFOLIO, OrderType.ON_CALL))
                .thenReturn(
                        List.of(
                                new ExecutedSubscriptionContract(
                                        "CT-LIVE",
                                        OrderType.ON_CALL,
                                        "EUR",
                                        NoticePeriod._48H,
                                        null,
                                        VALUE_DATE,
                                        new BigDecimal("1000000.00")),
                                new ExecutedSubscriptionContract(
                                        "CT-REDEEMED",
                                        OrderType.ON_CALL,
                                        "USD",
                                        NoticePeriod._24H,
                                        null,
                                        VALUE_DATE,
                                        new BigDecimal("2000000.00"))));
        when(orderRepository.findContractNumbersWithNonCancelledRedemption(List.of("CT-LIVE", "CT-REDEEMED")))
                .thenReturn(Set.of("CT-REDEEMED"));

        LiveContractsResult result = subject.listLiveContracts(PORTFOLIO, OrderType.ON_CALL);

        assertThat(result.contracts()).extracting(LiveContractResult::contractNumber).containsExactly("CT-LIVE");
    }
}
