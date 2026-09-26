package com.mmx.order.domain.policy;

import com.mmx.order.domain.model.Tenor;

import java.time.LocalDate;

public final class ContractLivenessPolicy {

    public boolean isOnCallLive(boolean hasNonCancelledRedemption) {
        return !hasNonCancelledRedemption;
    }

    public boolean isTermLive(LocalDate valueDate, Tenor tenor, LocalDate today) {
        return termEndDate(valueDate, tenor).isAfter(today);
    }

    public LocalDate termEndDate(LocalDate valueDate, Tenor tenor) {
        return switch (tenor) {
            case _1W -> valueDate.plusWeeks(1);
            case _2W -> valueDate.plusWeeks(2);
            case _1M -> valueDate.plusMonths(1);
            case _3M -> valueDate.plusMonths(3);
            case _6M -> valueDate.plusMonths(6);
            case _1Y -> valueDate.plusYears(1);
        };
    }
}
