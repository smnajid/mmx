package com.mmx.order.application.command;

import com.mmx.order.domain.model.NoticePeriod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AddOnCallRateCommand(
        String institutionCode,
        String currency,
        NoticePeriod noticePeriod,
        BigDecimal rate,
        LocalDate valueDate) {}
