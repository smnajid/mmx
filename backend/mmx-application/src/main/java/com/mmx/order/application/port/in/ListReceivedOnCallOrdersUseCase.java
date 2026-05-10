package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.ReceivedListView;

public interface ListReceivedOnCallOrdersUseCase {

    OrderPage listReceivedOnCallOrders(int page, int size, ReceivedListView receivedView);
}
