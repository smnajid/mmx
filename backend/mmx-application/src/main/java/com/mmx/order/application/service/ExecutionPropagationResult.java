package com.mmx.order.application.service;

import com.mmx.order.application.port.out.ExecutionHandoffRoutingContext;
import com.mmx.order.domain.model.MoneyMarketOrder;

/** Result of hub execute propagation: saved client order + outbox routing context. */
public record ExecutionPropagationResult(
        MoneyMarketOrder clientOrder, ExecutionHandoffRoutingContext handoffContext) {}
