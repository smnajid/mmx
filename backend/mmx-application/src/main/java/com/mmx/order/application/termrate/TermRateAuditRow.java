package com.mmx.order.application.termrate;

import com.mmx.order.domain.model.Tenor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TermRateAuditRow(
        LocalDate tradingDate,
        String institutionCode,
        String currency,
        Tenor tenor,
        BigDecimal rate,
        Instant uploadedAt,
        String uploadedBy) {}
