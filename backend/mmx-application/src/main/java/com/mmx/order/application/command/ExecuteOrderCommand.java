package com.mmx.order.application.command;

import com.mmx.order.domain.model.TraderId;

import java.math.BigDecimal;
import java.util.UUID;

public record ExecuteOrderCommand(
        UUID orderId,
        TraderId traderId,
        BigDecimal executedRate,
        String counterparty
) {}
