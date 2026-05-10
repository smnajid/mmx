package com.mmx.order.application.port.in;

public interface ListExecutedOnCallOrdersUseCase {

    OrderPage listExecutedOnCallOrders(int page, int size);
}
