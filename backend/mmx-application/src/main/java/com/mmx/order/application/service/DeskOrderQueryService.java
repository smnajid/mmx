package com.mmx.order.application.service;

import com.mmx.order.application.port.in.DeskOrderQueries;
import com.mmx.order.application.port.in.OrderPage;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OrderRepository;
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
    public OrderPage listReceivedTermOrders(int page, int size, ReceivedListView receivedView) {
        return listReceivedByWorkspace(OrderType.TERM, page, size, receivedView);
    }

    @Override
    public OrderPage listReceivedOnCallOrders(int page, int size, ReceivedListView receivedView) {
        return listReceivedByWorkspace(OrderType.ON_CALL, page, size, receivedView);
    }

    private OrderPage listReceivedByWorkspace(OrderType orderType, int page, int size, ReceivedListView receivedView) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (receivedView == ReceivedListView.ALL) {
            return orderRepository.findReceivedPageByOrderType(
                    orderType, Optional.empty(), Optional.empty(), page, size);
        }
        LocalDate start = businessTodayInclusive();
        LocalDate end = start.plusDays(2);
        return orderRepository.findReceivedPageByOrderType(
                orderType, Optional.of(start), Optional.of(end), page, size);
    }

    private LocalDate businessTodayInclusive() {
        return LocalDate.ofInstant(clock.now(), BUSINESS_CALENDAR_ZONE);
    }

    @Override
    public OrderPage listAssignedOrders(TraderId traderId, int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByAssignedTraderIdAndStatus(traderId, OrderStatus.ASSIGNED);
        return paginate(all, page, size);
    }

    @Override
    public OrderPage listAssignedTermOrders(int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByStatusAndOrderType(OrderStatus.ASSIGNED, OrderType.TERM);
        return paginate(all, page, size);
    }

    @Override
    public OrderPage listAssignedOnCallOrders(int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByStatusAndOrderType(OrderStatus.ASSIGNED, OrderType.ON_CALL);
        return paginate(all, page, size);
    }

    @Override
    public OrderPage listExecutedTermOrders(int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByStatusAndOrderType(OrderStatus.EXECUTED, OrderType.TERM);
        return paginate(all, page, size);
    }

    @Override
    public OrderPage listExecutedOnCallOrders(int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByStatusAndOrderType(OrderStatus.EXECUTED, OrderType.ON_CALL);
        return paginate(all, page, size);
    }

    @Override
    public Optional<MoneyMarketOrder> getOrderDetails(UUID orderId) {
        return orderRepository.findById(orderId);
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
