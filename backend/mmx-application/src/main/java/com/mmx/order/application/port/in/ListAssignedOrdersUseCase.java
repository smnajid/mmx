package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.TraderId;

public interface ListAssignedOrdersUseCase {

    OrderPage listAssignedOrders(TraderId traderId, int page, int size);
}
