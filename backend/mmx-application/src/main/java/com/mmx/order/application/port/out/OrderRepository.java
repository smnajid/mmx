package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.TraderId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    MoneyMarketOrder save(MoneyMarketOrder order);

    Optional<MoneyMarketOrder> findById(UUID id);

    Optional<MoneyMarketOrder> findByExternalOrderReference(ExternalOrderReference reference);

    List<MoneyMarketOrder> findByStatusAndOrderType(OrderStatus status, OrderType orderType);

    List<MoneyMarketOrder> findByAssignedTraderIdAndStatus(TraderId traderId, OrderStatus status);

    List<MoneyMarketOrder> findByAssignedTraderIdAndStatusAndOrderType(
            TraderId traderId, OrderStatus status, OrderType orderType);
}
