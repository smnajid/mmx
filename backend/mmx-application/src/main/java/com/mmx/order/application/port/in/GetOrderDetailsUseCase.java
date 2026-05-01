package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.MoneyMarketOrder;

import java.util.Optional;
import java.util.UUID;

public interface GetOrderDetailsUseCase {

    Optional<MoneyMarketOrder> getOrderDetails(UUID orderId);
}
