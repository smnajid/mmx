package com.mmx.order.adapter.out.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class GlobalAccountId implements Serializable {

    private String clientLegalEntityCode;
    private String hubLegalEntityCode;
    private String currency;

    public GlobalAccountId() {}

    public GlobalAccountId(String clientLegalEntityCode, String hubLegalEntityCode, String currency) {
        this.clientLegalEntityCode = clientLegalEntityCode;
        this.hubLegalEntityCode = hubLegalEntityCode;
        this.currency = currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GlobalAccountId that)) {
            return false;
        }
        return Objects.equals(clientLegalEntityCode, that.clientLegalEntityCode)
                && Objects.equals(hubLegalEntityCode, that.hubLegalEntityCode)
                && Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientLegalEntityCode, hubLegalEntityCode, currency);
    }
}
