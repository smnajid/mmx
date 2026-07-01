package com.mmx.order.application.service;

import com.mmx.order.application.port.in.OrderPage;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.ReceivedListView;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TraderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeskQueryScopeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");
    private static final ScopeContext LOC_TRADER = new ScopeContext(LOC, Role.TRADER);
    private static final ScopeContext PAR_TRADER = new ScopeContext(PAR, Role.TRADER);
    private static final TraderId TRADER_A = new TraderId("trader-a");

    @Mock
    OrderRepository orderRepository;

    @Mock
    Clock clock;

    @InjectMocks
    DeskOrderQueryService subject;

    @BeforeEach
    void freezeBusinessToday() {
        Instant noonParis =
                ZonedDateTime.of(2026, 5, 1, 12, 0, 0, 0, DeskOrderQueryService.BUSINESS_CALENDAR_ZONE)
                        .toInstant();
        lenient().when(clock.now()).thenReturn(noonParis);
    }

    @Test
    void listReceivedTermOrders_queriesWithActiveLegalEntity() {
        MoneyMarketOrder locOrder = orderFor(LOC, "T-loc");
        when(orderRepository.findReceivedPageByOrderType(
                        eq(LOC),
                        eq(OrderType.TERM),
                        eq(Optional.empty()),
                        eq(Optional.empty()),
                        eq(0),
                        eq(20)))
                .thenReturn(new OrderPage(List.of(locOrder), 1, 0, 20));

        OrderPage page = subject.listReceivedTermOrders(LOC_TRADER, 0, 20, ReceivedListView.ALL);

        assertThat(page.content()).containsExactly(locOrder);
        verify(orderRepository)
                .findReceivedPageByOrderType(LOC, OrderType.TERM, Optional.empty(), Optional.empty(), 0, 20);
    }

    @Test
    void listAssignedTermOrders_queriesWithActiveLegalEntity() {
        MoneyMarketOrder parOrder = assignedOrderFor(PAR, "A-par");
        when(orderRepository.findByStatusAndOrderType(PAR, OrderStatus.ASSIGNED, OrderType.TERM))
                .thenReturn(List.of(parOrder));

        OrderPage page = subject.listAssignedTermOrders(PAR_TRADER, 0, 20);

        assertThat(page.content()).containsExactly(parOrder);
        verify(orderRepository).findByStatusAndOrderType(PAR, OrderStatus.ASSIGNED, OrderType.TERM);
    }

    @Test
    void listExecutedOnCallOrders_queriesWithActiveLegalEntity() {
        MoneyMarketOrder locOrder = onCallOrderFor(LOC, "E-loc");
        when(orderRepository.findByStatusAndOrderType(LOC, OrderStatus.EXECUTED, OrderType.ON_CALL))
                .thenReturn(List.of(locOrder));

        OrderPage page = subject.listExecutedOnCallOrders(LOC_TRADER, 0, 20);

        assertThat(page.content()).containsExactly(locOrder);
        verify(orderRepository).findByStatusAndOrderType(LOC, OrderStatus.EXECUTED, OrderType.ON_CALL);
    }

    @Test
    void listAssignedOrders_queriesWithActiveLegalEntity() {
        MoneyMarketOrder locOrder = assignedOrderFor(LOC, "L-loc");
        when(orderRepository.findByAssignedTraderIdAndStatus(LOC, TRADER_A, OrderStatus.ASSIGNED))
                .thenReturn(List.of(locOrder));

        OrderPage page = subject.listAssignedOrders(LOC_TRADER, TRADER_A, 0, 20);

        assertThat(page.content()).containsExactly(locOrder);
        verify(orderRepository).findByAssignedTraderIdAndStatus(LOC, TRADER_A, OrderStatus.ASSIGNED);
    }

    @Test
    void getOrderDetails_crossEntity_returnsEmpty() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        MoneyMarketOrder locOrder = orderFor(LOC, "D-1");
        when(orderRepository.findById(id)).thenReturn(Optional.of(locOrder));

        assertThat(subject.getOrderDetails(PAR_TRADER, id)).isEmpty();
    }

    @Test
    void getOrderDetails_sameEntity_returnsOrder() {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        MoneyMarketOrder locOrder = orderFor(LOC, "D-2");
        when(orderRepository.findById(id)).thenReturn(Optional.of(locOrder));

        assertThat(subject.getOrderDetails(LOC_TRADER, id)).contains(locOrder);
    }

    private static MoneyMarketOrder orderFor(LegalEntityCode entity, String extRef) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference(extRef),
                entity,
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-1"),
                "EUR",
                new BigDecimal("100000.00"),
                TODAY.plusDays(5),
                new BigDecimal("2.00000000"),
                Tenor._1M,
                null,
                null,
                "BNKCO",
                "BankCo",
                TODAY);
    }

    private static MoneyMarketOrder assignedOrderFor(LegalEntityCode entity, String extRef) {
        MoneyMarketOrder order = orderFor(entity, extRef);
        order.assign(TRADER_A, Instant.parse("2026-05-01T12:00:00Z"));
        return order;
    }

    private static MoneyMarketOrder onCallOrderFor(LegalEntityCode entity, String extRef) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference(extRef),
                entity,
                OrderType.ON_CALL,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-1"),
                "CHF",
                new BigDecimal("200000.00"),
                TODAY.plusDays(5),
                new BigDecimal("1.50000000"),
                null,
                NoticePeriod._24H,
                null,
                "BNKCO",
                "BankCo",
                TODAY);
    }
}
