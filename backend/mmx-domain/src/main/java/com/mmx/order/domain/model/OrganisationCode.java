package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidLegalEntityException;

public record OrganisationCode(String value) {

    public OrganisationCode {
        if (value == null || value.length() != 4) {
            throw new InvalidLegalEntityException("OrganisationCode must be exactly 4 characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
