package com.mmx.order.application.ordercreation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderCreationCounterparty(
        String institutionCode,
        String displayName,
        BigDecimal rate,
        LocalDate rateDate,
        boolean indicative) {}
