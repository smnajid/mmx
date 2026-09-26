package com.mmx.order.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ClientEnablementId implements Serializable {

    @Column(name = "institution_code", nullable = false, length = 32)
    private String institutionCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    protected ClientEnablementId() {}

    public ClientEnablementId(String institutionCode, String currency) {
        this.institutionCode = institutionCode;
        this.currency = currency;
    }

    public String getInstitutionCode() {
        return institutionCode;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClientEnablementId that)) {
            return false;
        }
        return Objects.equals(institutionCode, that.institutionCode) && Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(institutionCode, currency);
    }
}
