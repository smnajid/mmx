package com.mmx.order.application.service;

import com.mmx.order.application.port.in.OrderPage;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);

    @Mock
    OrderRepository orderRepository;

    @InjectMocks
    OrderQueryService subject;

    @Test
    void listReceivedTermOrders_queriesRepositoryAndReturnsPagedSlice() {
        MoneyMarketOrder a = newTermReceived("T-a");
        MoneyMarketOrder b = newTermReceived("T-b");
        MoneyMarketOrder c = newTermReceived("T-c");
        when(orderRepository.findByStatusAndOrderType(OrderStatus.RECEIVED, OrderType.TERM))
                .thenReturn(List.of(a, b, c));

        OrderPage page = subject.listReceivedTermOrders(0, 2);

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.content()).containsExactly(a, b);
        verify(orderRepository).findByStatusAndOrderType(OrderStatus.RECEIVED, OrderType.TERM);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void listReceivedOnCallOrders_secondPageReturnsRemainingItems() {
        MoneyMarketOrder o1 = newOnCallReceived("O-1");
        MoneyMarketOrder o2 = newOnCallReceived("O-2");
        when(orderRepository.findByStatusAndOrderType(OrderStatus.RECEIVED, OrderType.ON_CALL))
                .thenReturn(List.of(o1, o2));

        OrderPage page = subject.listReceivedOnCallOrders(1, 1);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).containsExactly(o2);
        verify(orderRepository).findByStatusAndOrderType(OrderStatus.RECEIVED, OrderType.ON_CALL);
    }

    @Test
    void listReceivedTermOrders_pageBeyondData_returnsEmptyContentWithTotal() {
        MoneyMarketOrder only = newTermReceived("T-only");
        when(orderRepository.findByStatusAndOrderType(OrderStatus.RECEIVED, OrderType.TERM))
                .thenReturn(List.of(only));

        OrderPage page = subject.listReceivedTermOrders(3, 2);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void getOrderDetails_delegatesToRepository() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        MoneyMarketOrder order = newTermReceived("G-1");
        when(orderRepository.findById(eq(id))).thenReturn(Optional.of(order));

        assertThat(subject.getOrderDetails(id)).contains(order);
        verify(orderRepository).findById(id);
    }

    @Test
    void getOrderDetails_missingOrder_returnsEmpty() {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(subject.getOrderDetails(id)).isEmpty();
    }

    @Test
    void listReceivedTermOrders_rejectsNonPositiveSize() {
        assertThatThrownBy(() -> subject.listReceivedTermOrders(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    void listExecutedTermOrders_queriesExecutedTermOnly() {
        MoneyMarketOrder a = newTermReceived("E-T-a");
        when(orderRepository.findByStatusAndOrderType(OrderStatus.EXECUTED, OrderType.TERM))
                .thenReturn(List.of(a));

        OrderPage page = subject.listExecutedTermOrders(0, 20);

        assertThat(page.content()).containsExactly(a);
        verify(orderRepository).findByStatusAndOrderType(OrderStatus.EXECUTED, OrderType.TERM);
    }

    @Test
    void listExecutedOnCallOrders_queriesExecutedOnCallOnly() {
        MoneyMarketOrder o = newOnCallReceived("E-O-a");
        when(orderRepository.findByStatusAndOrderType(OrderStatus.EXECUTED, OrderType.ON_CALL))
                .thenReturn(List.of(o));

        OrderPage page = subject.listExecutedOnCallOrders(0, 20);

        assertThat(page.content()).containsExactly(o);
        verify(orderRepository).findByStatusAndOrderType(OrderStatus.EXECUTED, OrderType.ON_CALL);
    }

    private static MoneyMarketOrder newTermReceived(String extRef) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference(extRef),
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
                null,
                TODAY);
    }

    private static MoneyMarketOrder newOnCallReceived(String extRef) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference(extRef),
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
                null,
                TODAY);
    }
}
