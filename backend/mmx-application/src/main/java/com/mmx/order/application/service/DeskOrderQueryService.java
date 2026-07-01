package com.mmx.order.application.service;

import com.mmx.order.application.port.in.DeskOrderQueries;
import com.mmx.order.application.port.in.OrderPage;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.ReceivedListView;
import com.mmx.order.domain.model.TraderId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DeskOrderQueryService implements DeskOrderQueries {

    /** Business calendar for Received near-term window (spec 002 assumptions). */
    static final ZoneId BUSINESS_CALENDAR_ZONE = ZoneId.of("Europe/Paris");

    private final OrderRepository orderRepository;
    private final Clock clock;

    public DeskOrderQueryService(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    @Override
    public OrderPage listReceivedTermOrders(
            ScopeContext scope, int page, int size, ReceivedListView receivedView) {
        return listReceivedByWorkspace(scope, OrderType.TERM, page, size, receivedView);
    }

    @Override
    public OrderPage listReceivedOnCallOrders(
            ScopeContext scope, int page, int size, ReceivedListView receivedView) {
        return listReceivedByWorkspace(scope, OrderType.ON_CALL, page, size, receivedView);
    }

    private OrderPage listReceivedByWorkspace(
            ScopeContext scope, OrderType orderType, int page, int size, ReceivedListView receivedView) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        OrderPage pageResult;
        if (receivedView == ReceivedListView.ALL) {
            pageResult =
                    orderRepository.findReceivedPageByOrderType(
                            scope.legalEntityCode(),
                            orderType,
                            Optional.empty(),
                            Optional.empty(),
                            page,
                            size);
        } else {
            LocalDate start = businessTodayInclusive();
            LocalDate end = start.plusDays(2);
            pageResult =
                    orderRepository.findReceivedPageByOrderType(
                            scope.legalEntityCode(),
                            orderType,
                            Optional.of(start),
                            Optional.of(end),
                            page,
                            size);
        }
        return pageResult;
    }

    private LocalDate businessTodayInclusive() {
        return LocalDate.ofInstant(clock.now(), BUSINESS_CALENDAR_ZONE);
    }

    @Override
    public OrderPage listAssignedOrders(ScopeContext scope, TraderId traderId, int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByAssignedTraderIdAndStatus(
                        scope.legalEntityCode(), traderId, OrderStatus.ASSIGNED);
        return paginate(all, page, size);
    }

    @Override
    public OrderPage listAssignedTermOrders(ScopeContext scope, int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByStatusAndOrderType(
                        scope.legalEntityCode(), OrderStatus.ASSIGNED, OrderType.TERM);
        return paginate(all, page, size);
    }

    @Override
    public OrderPage listAssignedOnCallOrders(ScopeContext scope, int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByStatusAndOrderType(
                        scope.legalEntityCode(), OrderStatus.ASSIGNED, OrderType.ON_CALL);
        return paginate(all, page, size);
    }

    @Override
    public OrderPage listExecutedTermOrders(ScopeContext scope, int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByStatusAndOrderType(
                        scope.legalEntityCode(), OrderStatus.EXECUTED, OrderType.TERM);
        return paginate(all, page, size);
    }

    @Override
    public OrderPage listExecutedOnCallOrders(ScopeContext scope, int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByStatusAndOrderType(
                        scope.legalEntityCode(), OrderStatus.EXECUTED, OrderType.ON_CALL);
        return paginate(all, page, size);
    }

    @Override
    public Optional<MoneyMarketOrder> getOrderDetails(ScopeContext scope, UUID orderId) {
        return orderRepository
                .findById(orderId)
                .filter(order -> order.getLegalEntityCode().equals(scope.legalEntityCode()));
    }

    private static OrderPage paginate(List<MoneyMarketOrder> all, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        long totalElements = all.size();
        int fromIndex = page * size;
        if (fromIndex >= totalElements) {
            return new OrderPage(List.of(), totalElements, page, size);
        }
        int toIndex = Math.min(fromIndex + size, (int) totalElements);
        return new OrderPage(all.subList(fromIndex, toIndex), totalElements, page, size);
    }
}
