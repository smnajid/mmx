package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.model.Institution;

import java.util.Optional;

public final class OrderAgainstInstitutionPolicy {

    public void validateCatalogNotEmpty(boolean hasAnyInstitution) {
        if (!hasAnyInstitution) {
            throw new InvalidOrderException(
                    "No institutions onboarded; onboard at least one institution in Settings before executing");
        }
    }

    public void validateExecute(String institutionCode, Optional<Institution> institutionOpt) {
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new InvalidOrderException("institutionCode is required");
        }
        if (institutionOpt.isEmpty()) {
            throw new InvalidOrderException("Institution not found: " + institutionCode);
        }
        Institution institution = institutionOpt.get();
        if (!institution.isActive()) {
            throw new InvalidOrderException("Institution is not active: " + institutionCode);
        }
    }
}
