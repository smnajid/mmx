package com.mmx.order.application.port.in;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.domain.model.OrderStatus;

import java.util.UUID;

public interface ReceiveOrderUseCase {

    /**
     * Accepts an order from Portfolio Management. Idempotent: same external reference returns the
     * existing order ({@code newlyCreated == false}); new payload is never applied on duplicate receive.
     */
    Result receive(ReceiveOrderCommand command);

    record Result(UUID orderId, OrderStatus status, boolean newlyCreated) {}
}
