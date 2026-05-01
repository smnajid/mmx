package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidOrderException;

public record TraderId(String value) {

    public TraderId {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderException("TraderId must not be blank");
        }
        if (value.length() > 100) {
            throw new InvalidOrderException("TraderId must not exceed 100 characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
