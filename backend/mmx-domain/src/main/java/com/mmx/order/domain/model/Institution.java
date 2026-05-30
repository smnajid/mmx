package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidInstitutionException;

import java.util.Objects;

/** Catalog snapshot for an onboarded institution (bank). */
public final class Institution {

    private final String institutionCode;
    private final String displayName;
    private final boolean active;

    public Institution(String institutionCode, String displayName, boolean active) {
        this.institutionCode = validateCode(institutionCode);
        this.displayName = validateDisplayName(displayName);
        this.active = active;
    }

    public String getInstitutionCode() {
        return institutionCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return active;
    }

    public Institution withActive(boolean active) {
        return new Institution(institutionCode, displayName, active);
    }

    public static String validateDisplayName(String displayName) {
        if (displayName == null) {
            throw new InvalidInstitutionException("displayName is required");
        }
        String trimmed = displayName.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidInstitutionException("displayName must not be blank");
        }
        if (trimmed.length() > 128) {
            throw new InvalidInstitutionException("displayName must not exceed 128 characters");
        }
        return trimmed;
    }

    public static String validateCode(String institutionCode) {
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new InvalidInstitutionException("institutionCode is required");
        }
        if (institutionCode.length() > 32) {
            throw new InvalidInstitutionException("institutionCode must not exceed 32 characters");
        }
        return institutionCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Institution that)) {
            return false;
        }
        return active == that.active
                && Objects.equals(institutionCode, that.institutionCode)
                && Objects.equals(displayName, that.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(institutionCode, displayName, active);
    }
}
