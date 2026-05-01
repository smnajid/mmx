package com.mmx.order.adapter.in.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OrderDetailsResponse(
        UUID id,
        String externalOrderReference,
        String orderType,
        String orderOperation,
        String portfolioNumber,
        String currency,
        BigDecimal amount,
        LocalDate valueDate,
        BigDecimal minimumRate,
        String tenor,
        String noticePeriod,
        String sourceContractNumber,
        String desiredCounterpartyComment,
        String status,
        String assignedTraderId,
        Instant assignedAt,
        BigDecimal executedRate,
        String counterparty,
        Instant executionTime,
        String dealingReference,
        String generatedContractNumber,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {}
