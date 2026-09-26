package com.mmx.order.application.port.in;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.domain.model.OrderStatus;

import java.util.UUID;

public interface IntakeUseCase {

    /**
     * Accepts an order from Portfolio Management for TradingHub-native or TradingClient-routed intake.
     * Idempotent: same external reference returns the existing order ({@code newlyCreated == false}).
     */
    Result receive(ReceiveOrderCommand command);

    record Result(UUID orderId, OrderStatus status, boolean newlyCreated) {}
}
