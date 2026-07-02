package com.mmx.order.application.port.in;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.domain.model.OrderStatus;

import java.util.UUID;

public interface RouteOrderUseCase {

    Result route(ReceiveOrderCommand command);

    record Result(UUID clientOrderId, UUID hubOrderId, OrderStatus clientStatus, boolean newlyCreated) {}
}
