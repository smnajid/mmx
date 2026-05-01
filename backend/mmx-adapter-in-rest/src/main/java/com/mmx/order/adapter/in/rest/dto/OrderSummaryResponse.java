package com.mmx.order.adapter.in.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        String externalOrderReference,
        String orderType,
        String orderOperation,
        String portfolioNumber,
        String currency,
        BigDecimal amount,
        LocalDate valueDate,
        String status,
        String assignedTraderId
) {}
