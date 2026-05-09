package com.mmx.order.application.service;

import com.mmx.order.application.command.AssignOrderCommand;
import com.mmx.order.application.command.UnassignOrderCommand;
import com.mmx.order.application.port.in.AssignOrderUseCase;
import com.mmx.order.application.port.in.ListAssignedOrdersUseCase;
import com.mmx.order.application.port.in.OrderPage;
import com.mmx.order.application.port.in.UnassignOrderUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.TraderId;

import java.util.List;

public final class AssignmentService implements AssignOrderUseCase, UnassignOrderUseCase, ListAssignedOrdersUseCase {

    static final String EVENT_ORDER_ASSIGNED = "ORDER_ASSIGNED";
    static final String EVENT_ORDER_UNASSIGNED = "ORDER_UNASSIGNED";

    private final OrderRepository orderRepository;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public AssignmentService(OrderRepository orderRepository, AuditLogger auditLogger, Clock clock) {
        this.orderRepository = orderRepository;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Override
    public MoneyMarketOrder assign(AssignOrderCommand command) {
        MoneyMarketOrder order =
                orderRepository
                        .findById(command.orderId())
                        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        order.assign(command.traderId(), clock.now());
        MoneyMarketOrder saved = orderRepository.save(order);
        auditLogger.log(saved.getId(), EVENT_ORDER_ASSIGNED, command.traderId().value(), clock.now());
        return saved;
    }

    @Override
    public MoneyMarketOrder unassign(UnassignOrderCommand command) {
        MoneyMarketOrder order =
                orderRepository
                        .findById(command.orderId())
                        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        order.unassign(command.traderId(), clock.now());
        MoneyMarketOrder saved = orderRepository.save(order);
        auditLogger.log(saved.getId(), EVENT_ORDER_UNASSIGNED, command.traderId().value(), clock.now());
        return saved;
    }

    @Override
    public OrderPage listAssignedOrders(TraderId traderId, int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByAssignedTraderIdAndStatus(traderId, OrderStatus.ASSIGNED);
        return paginate(all, page, size);
    }

    public OrderPage listAssignedTermOrders(TraderId traderId, int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByAssignedTraderIdAndStatusAndOrderType(
                        traderId, OrderStatus.ASSIGNED, OrderType.TERM);
        return paginate(all, page, size);
    }

    public OrderPage listAssignedOnCallOrders(TraderId traderId, int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByAssignedTraderIdAndStatusAndOrderType(
                        traderId, OrderStatus.ASSIGNED, OrderType.ON_CALL);
        return paginate(all, page, size);
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
