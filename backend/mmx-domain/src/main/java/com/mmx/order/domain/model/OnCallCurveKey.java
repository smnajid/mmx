package com.mmx.order.domain.model;

import java.util.Objects;

public record OnCallCurveKey(String institutionCode, String currency, NoticePeriod noticePeriod) {

    public OnCallCurveKey {
        Objects.requireNonNull(institutionCode, "institutionCode must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(noticePeriod, "noticePeriod must not be null");
        if (institutionCode.isBlank()) {
            throw new IllegalArgumentException("institutionCode must not be blank");
        }
        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }
}
