package com.mmx.order.application.command;

import com.mmx.order.domain.model.TraderId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateOrderCommand(
        UUID orderId,
        TraderId traderId,
        BigDecimal amount,
        LocalDate valueDate
) {}
