package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidDelegatedGrantException;

import java.util.Objects;

/** Identity tuple of a delegated institution grant: (hubInstitutionCode, clientLegalEntityCode, currency). */
public record DelegatedGrantKey(
        String hubInstitutionCode, LegalEntityCode clientLegalEntityCode, String currency) {

    public DelegatedGrantKey {
        if (hubInstitutionCode == null || hubInstitutionCode.isBlank()) {
            throw new InvalidDelegatedGrantException("hubInstitutionCode is required");
        }
        Objects.requireNonNull(clientLegalEntityCode, "clientLegalEntityCode must not be null");
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new InvalidDelegatedGrantException("currency must be a three-letter ISO 4217 code");
        }
    }
}
