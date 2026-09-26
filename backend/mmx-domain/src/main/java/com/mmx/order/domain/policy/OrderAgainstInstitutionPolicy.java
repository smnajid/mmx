package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;

import java.util.Optional;

public final class OrderAgainstInstitutionPolicy {

    public void validateCatalogNotEmpty(boolean hasAnyInstitution) {
        if (!hasAnyInstitution) {
            throw new InvalidOrderException(
                    "No institutions onboarded; onboard at least one institution in Settings before executing");
        }
    }

    /**
     * Validates the order's institution for intake or execute: it exists, is open to new business when the
     * operation adds exposure, and holds the counterparty account for the OrderType.
     *
     * @return the counterparty account snapshot
     */
    public String validateExecute(
            String institutionCode,
            Optional<Institution> institutionOpt,
            OrderOperation operation,
            OrderType orderType) {
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new InvalidOrderException("institutionCode is required");
        }
        if (institutionOpt.isEmpty()) {
            throw new InvalidOrderException("Institution not found: " + institutionCode);
        }
        Institution institution = institutionOpt.get();
        NewBusinessPolicy.requireOpenForNewBusiness(institution, operation);
        return CounterpartyAccountPolicy.requireAccountFor(institution, orderType);
    }
}
