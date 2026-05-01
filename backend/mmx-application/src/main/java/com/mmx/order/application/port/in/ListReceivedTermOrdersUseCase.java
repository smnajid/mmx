package com.mmx.order.application.port.in;

public interface ListReceivedTermOrdersUseCase {

    OrderPage listReceivedTermOrders(int page, int size);
}
