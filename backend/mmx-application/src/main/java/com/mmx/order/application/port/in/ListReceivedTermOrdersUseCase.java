package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.ReceivedListView;

public interface ListReceivedTermOrdersUseCase {

    OrderPage listReceivedTermOrders(int page, int size, ReceivedListView receivedView);
}
