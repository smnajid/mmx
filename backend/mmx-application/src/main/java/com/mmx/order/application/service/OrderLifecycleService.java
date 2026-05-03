package com.mmx.order.application.service;

import com.mmx.order.application.command.CancelOrderCommand;
import com.mmx.order.application.command.RejectOrderCommand;
import com.mmx.order.application.port.in.CancelOrderUseCase;
import com.mmx.order.application.port.in.RejectOrderUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.model.MoneyMarketOrder;

public final class OrderLifecycleService implements CancelOrderUseCase, RejectOrderUseCase {

    static final String EVENT_ORDER_CANCELLED = "ORDER_CANCELLED";
    static final String EVENT_ORDER_REJECTED = "ORDER_REJECTED";

    private final OrderRepository orderRepository;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public OrderLifecycleService(OrderRepository orderRepository, AuditLogger auditLogger, Clock clock) {
        this.orderRepository = orderRepository;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Override
    public MoneyMarketOrder cancel(CancelOrderCommand command) {
        MoneyMarketOrder order =
                orderRepository
                        .findById(command.orderId())
                        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        var now = clock.now();
        order.cancel(now);
        MoneyMarketOrder saved = orderRepository.save(order);
        auditLogger.log(saved.getId(), EVENT_ORDER_CANCELLED, command.traderId().value(), now);
        return saved;
    }

    @Override
    public MoneyMarketOrder reject(RejectOrderCommand command) {
        validateReject(command);
        MoneyMarketOrder order =
                orderRepository
                        .findById(command.orderId())
                        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        var now = clock.now();
        order.reject(command.reason().trim(), now);
        MoneyMarketOrder saved = orderRepository.save(order);
        auditLogger.log(saved.getId(), EVENT_ORDER_REJECTED, command.traderId().value(), now);
        return saved;
    }

    private static void validateReject(RejectOrderCommand command) {
        if (command.reason() == null || command.reason().isBlank()) {
            throw new InvalidOrderException("reason is required");
        }
    }
}
