package com.mmx.order.application.service;

import com.mmx.order.application.command.AssignOrderCommand;
import com.mmx.order.application.command.UnassignOrderCommand;
import com.mmx.order.application.port.in.AssignOrderUseCase;
import com.mmx.order.application.port.in.UnassignOrderUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.model.MoneyMarketOrder;

public final class AssignmentService implements AssignOrderUseCase, UnassignOrderUseCase {

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
}
