package com.mmx.order.domain.model;

import java.util.Objects;

/** Treasury/nostro account reference for a client at a hub, keyed by currency. */
public record GlobalAccount(
        LegalEntityCode clientLegalEntityCode,
        LegalEntityCode hubLegalEntityCode,
        String currency,
        String accountRef) {

    public GlobalAccount {
        Objects.requireNonNull(clientLegalEntityCode, "clientLegalEntityCode must not be null");
        Objects.requireNonNull(hubLegalEntityCode, "hubLegalEntityCode must not be null");
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (accountRef == null || accountRef.isBlank()) {
            throw new IllegalArgumentException("accountRef must not be blank");
        }
    }

    public PortfolioNumber asPortfolioNumber() {
        return new PortfolioNumber(accountRef);
    }
}
