package com.mmx.order.application.service;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.ReceiveOrderUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.MoneyMarketOrder;

public final class ReceiveOrderService implements ReceiveOrderUseCase {

    static final String AUDIT_ACTOR_SYSTEM = "PORTFOLIO_MANAGEMENT";
    static final String EVENT_ORDER_RECEIVED = "ORDER_RECEIVED";
    static final String EVENT_DUPLICATE_RECEIVE_IGNORED = "DUPLICATE_RECEIVE_IGNORED";

    private final OrderRepository orderRepository;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public ReceiveOrderService(OrderRepository orderRepository, AuditLogger auditLogger, Clock clock) {
        this.orderRepository = orderRepository;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Override
    public Result receive(ReceiveOrderCommand command) {
        var existingOpt = orderRepository.findByExternalOrderReference(command.externalOrderReference());
        if (existingOpt.isPresent()) {
            MoneyMarketOrder existing = existingOpt.get();
            auditLogger.log(existing.getId(), EVENT_DUPLICATE_RECEIVE_IGNORED, AUDIT_ACTOR_SYSTEM, clock.now());
            return new Result(existing.getId(), existing.getStatus(), false);
        }

        MoneyMarketOrder created =
                MoneyMarketOrder.create(
                        command.externalOrderReference(),
                        command.orderType(),
                        command.orderOperation(),
                        command.portfolioNumber(),
                        command.currency(),
                        command.amount(),
                        command.valueDate(),
                        command.minimumRate(),
                        command.tenor(),
                        command.noticePeriod(),
                        command.sourceContractNumber(),
                        command.desiredCounterpartyComment(),
                        clock.today());

        MoneyMarketOrder saved = orderRepository.save(created);
        auditLogger.log(saved.getId(), EVENT_ORDER_RECEIVED, AUDIT_ACTOR_SYSTEM, clock.now());
        return new Result(saved.getId(), saved.getStatus(), true);
    }
}
