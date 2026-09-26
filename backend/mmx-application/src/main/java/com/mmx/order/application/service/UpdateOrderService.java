package com.mmx.order.application.service;

import com.mmx.order.application.command.UpdateOrderCommand;
import com.mmx.order.application.port.in.UpdateAssignedOrderUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OpenPositionPort;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OpenContractPosition;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.policy.OrderAgainstCurrencyPolicy;

import java.util.Objects;
import java.util.Optional;

public final class UpdateOrderService implements UpdateAssignedOrderUseCase {

    static final String EVENT_ORDER_UPDATED = "ORDER_UPDATED";

    private final OrderRepository orderRepository;
    private final ManagedCurrencyRepository managedCurrencyRepository;
    private final OpenPositionPort openPositionPort;
    private final OrderAgainstCurrencyPolicy currencyPolicy;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public UpdateOrderService(
            OrderRepository orderRepository,
            ManagedCurrencyRepository managedCurrencyRepository,
            OpenPositionPort openPositionPort,
            AuditLogger auditLogger,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.managedCurrencyRepository = managedCurrencyRepository;
        this.openPositionPort = openPositionPort;
        this.currencyPolicy = new OrderAgainstCurrencyPolicy();
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Override
    public MoneyMarketOrder update(UpdateOrderCommand command) {
        validate(command);
        MoneyMarketOrder order =
                orderRepository.findById(command.orderId()).orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        if (command.amount() != null) {
            var currencyOpt = managedCurrencyRepository.findByCode(order.getCurrency());
            Optional<OpenContractPosition> openPosition =
                    order.getOrderOperation() == OrderOperation.DECREASE && order.getSourceContractNumber() != null
                            ? openPositionPort.findOpenByContractNumber(order.getSourceContractNumber())
                            : Optional.empty();
            currencyPolicy.validateAmountUpdate(
                    currencyOpt,
                    order.getCurrency(),
                    order.getOrderType(),
                    order.getOrderOperation(),
                    command.amount(),
                    order.getTenor(),
                    order.getNoticePeriod(),
                    openPosition);
        }

        var now = clock.now();
        var today = clock.today();
        order.update(command.amount(), command.valueDate(), command.traderId(), today, now);

        MoneyMarketOrder saved = orderRepository.save(order);
        auditLogger.log(saved.getId(), EVENT_ORDER_UPDATED, command.traderId().value(), now);
        return saved;
    }

    private static void validate(UpdateOrderCommand command) {
        if (command.amount() == null && command.valueDate() == null) {
            throw new InvalidOrderException("At least one of amount or valueDate must be provided");
        }
        Objects.requireNonNull(command.orderId(), "orderId");
        Objects.requireNonNull(command.traderId(), "traderId");
    }
}
