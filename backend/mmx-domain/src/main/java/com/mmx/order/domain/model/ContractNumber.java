package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidOrderException;

public record ContractNumber(String value) {

    public ContractNumber {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderException("ContractNumber must not be blank");
        }
        if (value.length() > 50) {
            throw new InvalidOrderException("ContractNumber must not exceed 50 characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
