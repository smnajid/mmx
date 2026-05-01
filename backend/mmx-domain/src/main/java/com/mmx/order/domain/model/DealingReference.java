package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidOrderException;

public record DealingReference(String value) {

    public DealingReference {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderException("DealingReference must not be blank");
        }
        if (value.length() > 50) {
            throw new InvalidOrderException("DealingReference must not exceed 50 characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
