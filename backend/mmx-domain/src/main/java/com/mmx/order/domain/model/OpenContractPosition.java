package com.mmx.order.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record OpenContractPosition(ContractNumber contractNumber, String currency, BigDecimal outstandingAmount) {

    public OpenContractPosition {
        Objects.requireNonNull(contractNumber, "contractNumber");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(outstandingAmount, "outstandingAmount");
        ManagedCurrency.validateCode(currency);
        if (outstandingAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("outstandingAmount must be >= 0");
        }
    }
}
