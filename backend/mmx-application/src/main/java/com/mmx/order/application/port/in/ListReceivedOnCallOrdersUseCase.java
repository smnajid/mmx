package com.mmx.order.application.port.in;

public interface ListReceivedOnCallOrdersUseCase {

    OrderPage listReceivedOnCallOrders(int page, int size);
}
