package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidOrderException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

public record ExecutionDetails(
        BigDecimal executedRate,
        String counterparty,
        Instant executionTime,
        DealingReference dealingReference,
        ContractNumber generatedContractNumber
) {

    public ExecutionDetails {
        Objects.requireNonNull(executedRate, "executedRate must not be null");
        Objects.requireNonNull(executionTime, "executionTime must not be null");
        Objects.requireNonNull(dealingReference, "dealingReference must not be null");
        Objects.requireNonNull(generatedContractNumber, "generatedContractNumber must not be null");

        if (executedRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidOrderException("executedRate must be >= 0");
        }
        if (counterparty == null || counterparty.isBlank()) {
            throw new InvalidOrderException("counterparty must not be blank");
        }
        if (counterparty.length() > 200) {
            throw new InvalidOrderException("counterparty must not exceed 200 characters");
        }

        executedRate = executedRate.setScale(8, RoundingMode.UNNECESSARY);
    }
}
