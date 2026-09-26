package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidOrderException;

public record MmxUserId(String value) {

    public MmxUserId {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderException("MmxUserId must not be blank");
        }
        if (value.length() > 100) {
            throw new InvalidOrderException("MmxUserId must not exceed 100 characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
