package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.MoneyMarketOrder;

/**
 * Schedules durable execution handoff toward back-office (transactional outbox row + Kafka relay).
 */
public interface ExecutionHandoffOutbox {

    void schedule(MoneyMarketOrder executedOrder, ExecutionHandoffRoutingContext routingContext);

    default void schedule(MoneyMarketOrder executedOrder) {
        schedule(executedOrder, ExecutionHandoffRoutingContext.none());
    }
}
