package com.mmx.order.adapter.out.persistence.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class TermRateId implements Serializable {

    private LocalDate tradingDate;
    private String institutionCode;
    private String currency;
    private String tenor;

    public TermRateId() {}

    public TermRateId(LocalDate tradingDate, String institutionCode, String currency, String tenor) {
        this.tradingDate = tradingDate;
        this.institutionCode = institutionCode;
        this.currency = currency;
        this.tenor = tenor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TermRateId that)) {
            return false;
        }
        return Objects.equals(tradingDate, that.tradingDate)
                && Objects.equals(institutionCode, that.institutionCode)
                && Objects.equals(currency, that.currency)
                && Objects.equals(tenor, that.tenor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradingDate, institutionCode, currency, tenor);
    }
}
