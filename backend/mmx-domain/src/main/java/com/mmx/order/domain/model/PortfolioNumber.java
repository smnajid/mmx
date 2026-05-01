package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidOrderException;

public record PortfolioNumber(String value) {

    public PortfolioNumber {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderException("PortfolioNumber must not be blank");
        }
        if (value.length() > 50) {
            throw new InvalidOrderException("PortfolioNumber must not exceed 50 characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
