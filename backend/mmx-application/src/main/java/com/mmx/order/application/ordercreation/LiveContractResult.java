package com.mmx.order.application.ordercreation;

import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.Tenor;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LiveContractResult(
        String contractNumber,
        OrderType orderType,
        String currency,
        NoticePeriod noticePeriod,
        Tenor tenor,
        LocalDate valueDate,
        LocalDate endDate,
        BigDecimal originalAmount) {}
