package com.mmx.order.application.service;

import com.mmx.order.application.command.ExecuteOrderCommand;
import com.mmx.order.application.port.in.ExecuteOrderUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.ExecutionHandoffOutbox;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;

public final class ExecuteOrderService implements ExecuteOrderUseCase {

    static final String EVENT_ORDER_EXECUTED = "ORDER_EXECUTED";

    private final OrderRepository orderRepository;
    private final ReferenceGenerator referenceGenerator;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final ExecutionHandoffOutbox executionHandoffOutbox;

    public ExecuteOrderService(
            OrderRepository orderRepository,
            ReferenceGenerator referenceGenerator,
            AuditLogger auditLogger,
            Clock clock,
            ExecutionHandoffOutbox executionHandoffOutbox) {
        this.orderRepository = orderRepository;
        this.referenceGenerator = referenceGenerator;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.executionHandoffOutbox = executionHandoffOutbox;
    }

    @Override
    public MoneyMarketOrder execute(ExecuteOrderCommand command) {
        validate(command);
        MoneyMarketOrder order =
                orderRepository.findById(command.orderId()).orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        var now = clock.now();
        ContractNumber contractNumber = resolveExecutionContractNumber(order);
        var dealingReference = referenceGenerator.generateDealingReference();

        order.execute(
                command.executedRate(),
                command.counterparty(),
                dealingReference,
                contractNumber,
                command.traderId(),
                now);

        order.markHandoffPending();
        MoneyMarketOrder saved = orderRepository.save(order);
        executionHandoffOutbox.schedule(saved);
        auditLogger.log(saved.getId(), EVENT_ORDER_EXECUTED, command.traderId().value(), now);
        return saved;
    }

    private static void validate(ExecuteOrderCommand command) {
        if (command.executedRate() == null) {
            throw new InvalidOrderException("executedRate is required");
        }
        if (command.counterparty() == null || command.counterparty().isBlank()) {
            throw new InvalidOrderException("counterparty is required");
        }
    }

    private ContractNumber resolveExecutionContractNumber(MoneyMarketOrder order) {
        if (order.getOrderOperation() == OrderOperation.SUBSCRIPTION) {
            return referenceGenerator.generateContractNumber();
        }
        ContractNumber source = order.getSourceContractNumber();
        if (source == null) {
            throw new InvalidOrderException(
                    "sourceContractNumber is required for " + order.getOrderOperation() + " operations");
        }
        return source;
    }
}
