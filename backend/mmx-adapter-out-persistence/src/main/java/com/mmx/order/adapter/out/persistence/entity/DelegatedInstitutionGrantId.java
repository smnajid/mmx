package com.mmx.order.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class DelegatedInstitutionGrantId implements Serializable {

    @Column(name = "hub_institution_code", nullable = false, length = 32)
    private String hubInstitutionCode;

    @Column(name = "client_legal_entity_code", nullable = false, length = 3)
    private String clientLegalEntityCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    protected DelegatedInstitutionGrantId() {}

    public DelegatedInstitutionGrantId(String hubInstitutionCode, String clientLegalEntityCode, String currency) {
        this.hubInstitutionCode = hubInstitutionCode;
        this.clientLegalEntityCode = clientLegalEntityCode;
        this.currency = currency;
    }

    public String getHubInstitutionCode() {
        return hubInstitutionCode;
    }

    public String getClientLegalEntityCode() {
        return clientLegalEntityCode;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DelegatedInstitutionGrantId that)) {
            return false;
        }
        return Objects.equals(hubInstitutionCode, that.hubInstitutionCode)
                && Objects.equals(clientLegalEntityCode, that.clientLegalEntityCode)
                && Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hubInstitutionCode, clientLegalEntityCode, currency);
    }
}
