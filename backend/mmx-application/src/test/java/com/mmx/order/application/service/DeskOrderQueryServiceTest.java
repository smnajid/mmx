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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeskOrderQueryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    /** Inclusive window end when "today" is 2026-05-01 in Europe/Paris (today + 2 calendar days). */
    private static final LocalDate NEAR_TERM_END = LocalDate.of(2026, 5, 3);
    private static final TraderId TRADER_A = new TraderId("trader-a");
    private static final TraderId TRADER_B = new TraderId("trader-b");
    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final ScopeContext LOC_TRADER = new ScopeContext(LOC, Role.TRADER);

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
    void listReceivedTermOrders_nearTerm_queriesDateWindow() {
        MoneyMarketOrder a = newTermReceived("T-a");
        MoneyMarketOrder b = newTermReceived("T-b");
        when(orderRepository.findReceivedPageByOrderType(
                        eq(LOC),
                        eq(OrderType.TERM),
                        eq(Optional.of(TODAY)),
                        eq(Optional.of(NEAR_TERM_END)),
                        eq(0),
                        eq(2)))
                .thenReturn(new OrderPage(List.of(a, b), 3, 0, 2));

        OrderPage page = subject.listReceivedTermOrders(LOC_TRADER, 0, 2, ReceivedListView.NEAR_TERM);

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.content()).containsExactly(a, b);
        verify(orderRepository)
                .findReceivedPageByOrderType(
                        LOC, OrderType.TERM, Optional.of(TODAY), Optional.of(NEAR_TERM_END), 0, 2);
    }

    @Test
    void listReceivedTermOrders_all_skipsValueDateFilter() {
        MoneyMarketOrder a = newTermReceived("T-a");
        when(orderRepository.findReceivedPageByOrderType(
                        eq(LOC), eq(OrderType.TERM), eq(Optional.empty()), eq(Optional.empty()), eq(0), eq(20)))
                .thenReturn(new OrderPage(List.of(a), 1, 0, 20));

        OrderPage page = subject.listReceivedTermOrders(LOC_TRADER, 0, 20, ReceivedListView.ALL);

        assertThat(page.content()).containsExactly(a);
        verify(orderRepository).findReceivedPageByOrderType(LOC, OrderType.TERM, Optional.empty(), Optional.empty(), 0, 20);
    }

    @Test
    void listReceivedOnCallOrders_secondPageUsesWindow() {
        MoneyMarketOrder o2 = newOnCallReceived("O-2");
        when(orderRepository.findReceivedPageByOrderType(
                        eq(LOC),
                        eq(OrderType.ON_CALL),
                        eq(Optional.of(TODAY)),
                        eq(Optional.of(NEAR_TERM_END)),
                        eq(1),
                        eq(1)))
                .thenReturn(new OrderPage(List.of(o2), 2, 1, 1));

        OrderPage page = subject.listReceivedOnCallOrders(LOC_TRADER, 1, 1, ReceivedListView.NEAR_TERM);

        assertThat(page.content()).containsExactly(o2);
        verify(orderRepository)
                .findReceivedPageByOrderType(
                        LOC, OrderType.ON_CALL, Optional.of(TODAY), Optional.of(NEAR_TERM_END), 1, 1);
    }

    @Test
    void listReceivedTermOrders_pageBeyondData_returnsEmptyContentWithTotal() {
        when(orderRepository.findReceivedPageByOrderType(
                        eq(LOC),
                        eq(OrderType.TERM),
                        eq(Optional.of(TODAY)),
                        eq(Optional.of(NEAR_TERM_END)),
                        eq(3),
                        eq(2)))
                .thenReturn(new OrderPage(List.of(), 1, 3, 2));

        OrderPage page = subject.listReceivedTermOrders(LOC_TRADER, 3, 2, ReceivedListView.NEAR_TERM);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void getOrderDetails_delegatesToRepository() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        MoneyMarketOrder order = newTermReceived("G-1");
        when(orderRepository.findById(eq(id))).thenReturn(Optional.of(order));

        assertThat(subject.getOrderDetails(LOC_TRADER, id)).contains(order);
        verify(orderRepository).findById(id);
    }

    @Test
    void getOrderDetails_missingOrder_returnsEmpty() {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(subject.getOrderDetails(LOC_TRADER, id)).isEmpty();
    }

    @Test
    void listReceivedTermOrders_rejectsNonPositiveSize() {
        assertThatThrownBy(() -> subject.listReceivedTermOrders(LOC_TRADER, 0, 0, ReceivedListView.NEAR_TERM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    void listExecutedTermOrders_queriesExecutedTermOnly() {
        MoneyMarketOrder a = newTermReceived("E-T-a");
        when(orderRepository.findByStatusAndOrderType(LOC, OrderStatus.EXECUTED, OrderType.TERM))
                .thenReturn(List.of(a));

        OrderPage page = subject.listExecutedTermOrders(LOC_TRADER, 0, 20);

        assertThat(page.content()).containsExactly(a);
        verify(orderRepository).findByStatusAndOrderType(LOC, OrderStatus.EXECUTED, OrderType.TERM);
    }

    @Test
    void listExecutedOnCallOrders_queriesExecutedOnCallOnly() {
        MoneyMarketOrder o = newOnCallReceived("E-O-a");
        when(orderRepository.findByStatusAndOrderType(LOC, OrderStatus.EXECUTED, OrderType.ON_CALL))
                .thenReturn(List.of(o));

        OrderPage page = subject.listExecutedOnCallOrders(LOC_TRADER, 0, 20);

        assertThat(page.content()).containsExactly(o);
        verify(orderRepository).findByStatusAndOrderType(LOC, OrderStatus.EXECUTED, OrderType.ON_CALL);
    }

    @Test
    void listAssignedOrders_returns_only_matching_trader() {
        MoneyMarketOrder forA = newTermReceived("A-1");
        forA.assign(TRADER_A, Instant.parse("2026-05-01T12:00:00Z"));

        when(orderRepository.findByAssignedTraderIdAndStatus(LOC, TRADER_A, OrderStatus.ASSIGNED))
                .thenReturn(List.of(forA));

        OrderPage page = subject.listAssignedOrders(LOC_TRADER, TRADER_A, 0, 20);

        assertThat(page.content()).containsExactly(forA);
        assertThat(page.totalElements()).isEqualTo(1);

        ArgumentCaptor<TraderId> traderCaptor = ArgumentCaptor.forClass(TraderId.class);
        verify(orderRepository).findByAssignedTraderIdAndStatus(eq(LOC), traderCaptor.capture(), eq(OrderStatus.ASSIGNED));
        assertThat(traderCaptor.getValue()).isEqualTo(TRADER_A);
    }

    @Test
    void listAssignedTermOrders_deskWide_usesStatusAndOrderType() {
        MoneyMarketOrder termAssignedToA = newTermReceived("T-A");
        termAssignedToA.assign(TRADER_A, Instant.parse("2026-05-01T12:00:00Z"));
        MoneyMarketOrder termAssignedToB = newTermReceived("T-B");
        termAssignedToB.assign(TRADER_B, Instant.parse("2026-05-01T12:00:00Z"));
        when(orderRepository.findByStatusAndOrderType(LOC, OrderStatus.ASSIGNED, OrderType.TERM))
                .thenReturn(List.of(termAssignedToA, termAssignedToB));

        OrderPage page = subject.listAssignedTermOrders(LOC_TRADER, 0, 20);

        assertThat(page.content()).containsExactly(termAssignedToA, termAssignedToB);
        verify(orderRepository).findByStatusAndOrderType(LOC, OrderStatus.ASSIGNED, OrderType.TERM);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void listAssignedOnCallOrders_deskWide_usesStatusAndOrderType() {
        MoneyMarketOrder onCall = newOnCallReceived("O-1");
        onCall.assign(TRADER_A, Instant.parse("2026-05-01T12:00:00Z"));
        when(orderRepository.findByStatusAndOrderType(LOC, OrderStatus.ASSIGNED, OrderType.ON_CALL))
                .thenReturn(List.of(onCall));

        OrderPage page = subject.listAssignedOnCallOrders(LOC_TRADER, 0, 20);

        assertThat(page.content()).containsExactly(onCall);
        verify(orderRepository).findByStatusAndOrderType(LOC, OrderStatus.ASSIGNED, OrderType.ON_CALL);
    }

    private static MoneyMarketOrder newTermReceived(String extRef) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference(extRef),
                LOC,
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-1"),
                "EUR",
                new BigDecimal("100000.00"),
                TODAY.plusDays(5),
                new BigDecimal("2.00000000"),
                Tenor._1M, null, null, "BNKCO", "BankCo",
                TODAY);
    }

    private static MoneyMarketOrder newOnCallReceived(String extRef) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference(extRef),
                LOC,
                OrderType.ON_CALL,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-1"),
                "CHF",
                new BigDecimal("200000.00"),
                TODAY.plusDays(5),
                new BigDecimal("1.50000000"),
                null,
                NoticePeriod._24H, null, "BNKCO", "BankCo",
                TODAY);
    }
}
