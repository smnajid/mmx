package com.mmx.order.application.service;

import com.mmx.order.domain.model.MoneyMarketOrder;

import java.time.Instant;

/** Applies hub-side terminal outcomes to the linked client-side order in a routed pair. */
public interface RoutedOrderOutcomePropagation {

    ExecutionPropagationResult propagateExecution(MoneyMarketOrder hubOrder);

    void propagateCancel(MoneyMarketOrder hubOrder, Instant now);

    void propagateReject(MoneyMarketOrder hubOrder, String reason, Instant now);
}
