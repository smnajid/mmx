package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.Tenor;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExecutedSubscriptionContract(
        String contractNumber,
        OrderType orderType,
        String currency,
        NoticePeriod noticePeriod,
        Tenor tenor,
        LocalDate valueDate,
        BigDecimal originalAmount) {}
