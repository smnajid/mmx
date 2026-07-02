package com.mmx.order.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "global_account")
@IdClass(GlobalAccountId.class)
public class GlobalAccountEntity {

    @Id
    @Column(name = "client_legal_entity_code", nullable = false, length = 3)
    private String clientLegalEntityCode;

    @Id
    @Column(name = "hub_legal_entity_code", nullable = false, length = 3)
    private String hubLegalEntityCode;

    @Id
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "account_ref", nullable = false, length = 50)
    private String accountRef;

    public GlobalAccountEntity() {}

    public GlobalAccountEntity(
            String clientLegalEntityCode,
            String hubLegalEntityCode,
            String currency,
            String accountRef) {
        this.clientLegalEntityCode = clientLegalEntityCode;
        this.hubLegalEntityCode = hubLegalEntityCode;
        this.currency = currency;
        this.accountRef = accountRef;
    }

    public String getClientLegalEntityCode() {
        return clientLegalEntityCode;
    }

    public void setClientLegalEntityCode(String clientLegalEntityCode) {
        this.clientLegalEntityCode = clientLegalEntityCode;
    }

    public String getHubLegalEntityCode() {
        return hubLegalEntityCode;
    }

    public void setHubLegalEntityCode(String hubLegalEntityCode) {
        this.hubLegalEntityCode = hubLegalEntityCode;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getAccountRef() {
        return accountRef;
    }

    public void setAccountRef(String accountRef) {
        this.accountRef = accountRef;
    }
}
