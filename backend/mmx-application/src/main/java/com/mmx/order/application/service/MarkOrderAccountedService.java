package com.mmx.order.application.service;

import com.mmx.order.application.port.in.MarkOrderAccountedUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderStatus;

import java.util.UUID;

public final class MarkOrderAccountedService implements MarkOrderAccountedUseCase {

    static final String AUDIT_ACTOR_BACK_OFFICE = "BACK_OFFICE";
    static final String EVENT_ORDER_ACCOUNTED = "ORDER_ACCOUNTED";

    private final OrderRepository orderRepository;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public MarkOrderAccountedService(
            OrderRepository orderRepository, AuditLogger auditLogger, Clock clock) {
        this.orderRepository = orderRepository;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Override
    public void markAccounted(UUID orderId) {
        MoneyMarketOrder order =
                orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.ACCOUNTED) {
            return;
        }

        var now = clock.now();
        order.markAccounted(now);
        orderRepository.save(order);
        auditLogger.log(orderId, EVENT_ORDER_ACCOUNTED, AUDIT_ACTOR_BACK_OFFICE, now);
    }
}
