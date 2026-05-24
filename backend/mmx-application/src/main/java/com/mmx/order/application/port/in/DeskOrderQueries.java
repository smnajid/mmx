package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.ReceivedListView;
import com.mmx.order.domain.model.TraderId;

import java.util.Optional;
import java.util.UUID;

/** Inbound port for Trader desk queue reads and order detail lookup. */
public interface DeskOrderQueries {

    OrderPage listReceivedTermOrders(int page, int size, ReceivedListView receivedView);

    OrderPage listReceivedOnCallOrders(int page, int size, ReceivedListView receivedView);

    OrderPage listAssignedTermOrders(int page, int size);

    OrderPage listAssignedOnCallOrders(int page, int size);

    OrderPage listAssignedOrders(TraderId traderId, int page, int size);

    OrderPage listExecutedTermOrders(int page, int size);

    OrderPage listExecutedOnCallOrders(int page, int size);

    Optional<MoneyMarketOrder> getOrderDetails(UUID orderId);
}
