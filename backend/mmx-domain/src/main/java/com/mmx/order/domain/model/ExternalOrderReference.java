package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidOrderException;

public record ExternalOrderReference(String value) {

    public ExternalOrderReference {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderException("ExternalOrderReference must not be blank");
        }
        if (value.length() > 100) {
            throw new InvalidOrderException("ExternalOrderReference must not exceed 100 characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
