package com.mmx.order.application.termrate;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedTermRateRow(
        int line,
        LocalDate tradingDate,
        String institutionCode,
        String currency,
        String tenorCode,
        BigDecimal rate) {}
