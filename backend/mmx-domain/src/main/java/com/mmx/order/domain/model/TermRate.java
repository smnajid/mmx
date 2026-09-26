package com.mmx.order.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class TermRate {

    private final LocalDate tradingDate;
    private final String institutionCode;
    private final String currency;
    private final Tenor tenor;
    private final BigDecimal rate;

    public TermRate(
            LocalDate tradingDate, String institutionCode, String currency, Tenor tenor, BigDecimal rate) {
        this.tradingDate = Objects.requireNonNull(tradingDate, "tradingDate");
        this.institutionCode = requireNonBlank(institutionCode, "institutionCode");
        this.currency = ManagedCurrency.validateCode(currency);
        this.tenor = Objects.requireNonNull(tenor, "tenor");
        this.rate = Objects.requireNonNull(rate, "rate");
    }

    public LocalDate getTradingDate() {
        return tradingDate;
    }

    public String getInstitutionCode() {
        return institutionCode;
    }

    public String getCurrency() {
        return currency;
    }

    public Tenor getTenor() {
        return tenor;
    }

    public BigDecimal getRate() {
        return rate;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

}
