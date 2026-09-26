package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidLegalEntityException;

public record LegalEntityCode(String value) {

    public LegalEntityCode {
        if (value == null || value.length() != 3) {
            throw new InvalidLegalEntityException("LegalEntityCode must be exactly 3 characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
