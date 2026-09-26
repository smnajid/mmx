package com.mmx.order.application.port.in;

import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.ReceivedListView;
import com.mmx.order.domain.model.TraderId;

import java.util.Optional;
import java.util.UUID;

/** Inbound port for Trader desk queue reads and order detail lookup. */
public interface DeskOrderQueries {

    OrderPage listReceivedTermOrders(ScopeContext scope, int page, int size, ReceivedListView receivedView);

    OrderPage listReceivedOnCallOrders(ScopeContext scope, int page, int size, ReceivedListView receivedView);

    OrderPage listAssignedTermOrders(ScopeContext scope, int page, int size);

    OrderPage listAssignedOnCallOrders(ScopeContext scope, int page, int size);

    OrderPage listAssignedOrders(ScopeContext scope, TraderId traderId, int page, int size);

    OrderPage listExecutedTermOrders(ScopeContext scope, int page, int size);

    OrderPage listExecutedOnCallOrders(ScopeContext scope, int page, int size);

    Optional<MoneyMarketOrder> getOrderDetails(ScopeContext scope, UUID orderId);
}
