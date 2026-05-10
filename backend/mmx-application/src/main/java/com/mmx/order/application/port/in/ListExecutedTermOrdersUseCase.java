package com.mmx.order.application.port.in;

public interface ListExecutedTermOrdersUseCase {

    OrderPage listExecutedTermOrders(int page, int size);
}
