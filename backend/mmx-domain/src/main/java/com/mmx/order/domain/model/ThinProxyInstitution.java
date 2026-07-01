package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidInstitutionException;

import java.util.Objects;

/**
 * A TradingClient's thin-proxy institution referencing a hub native institution. The display name is
 * derived deterministically as "{@code {hubInstitution.displayName} via {hubLegalEntityCode}}". A
 * proxy carries its own client-scope {@code institutionCode} but no independent rate curves — rates
 * are the hub's rates for the referenced native institution, read in-process.
 */
public final class ThinProxyInstitution {

    private final String institutionCode;
    private final String displayName;
    private final LegalEntityCode hubLegalEntityCode;
    private final String hubInstitutionCode;
    private final boolean active;

    public ThinProxyInstitution(
            String institutionCode,
            String displayName,
            LegalEntityCode hubLegalEntityCode,
            String hubInstitutionCode,
            boolean active) {
        this.institutionCode = Institution.validateCode(institutionCode);
        this.displayName = Institution.validateDisplayName(displayName);
        this.hubLegalEntityCode = Objects.requireNonNull(hubLegalEntityCode, "hubLegalEntityCode");
        this.hubInstitutionCode = Institution.validateCode(hubInstitutionCode);
        this.active = active;
    }

    public static ThinProxyInstitution forHubInstitution(
            String institutionCode, Institution hubInstitution, LegalEntityCode hubLegalEntityCode) {
        Objects.requireNonNull(hubInstitution, "hubInstitution");
        return new ThinProxyInstitution(
                institutionCode,
                deriveDisplayName(hubInstitution.getDisplayName(), hubLegalEntityCode),
                hubLegalEntityCode,
                hubInstitution.getInstitutionCode(),
                true);
    }

    public static String deriveDisplayName(String hubInstitutionDisplayName, LegalEntityCode hubLegalEntityCode) {
        if (hubInstitutionDisplayName == null || hubInstitutionDisplayName.isBlank()) {
            throw new InvalidInstitutionException("hubInstitution displayName is required for proxy naming");
        }
        return hubInstitutionDisplayName.trim() + " via " + hubLegalEntityCode.value();
    }

    public String getInstitutionCode() {
        return institutionCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LegalEntityCode getHubLegalEntityCode() {
        return hubLegalEntityCode;
    }

    public String getHubInstitutionCode() {
        return hubInstitutionCode;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isProxy() {
        return true;
    }

    public String hubRateLookupInstitutionCode() {
        return hubInstitutionCode;
    }

    public LegalEntityCode hubRateLookupLegalEntityCode() {
        return hubLegalEntityCode;
    }

    public ThinProxyInstitution withActive(boolean active) {
        return new ThinProxyInstitution(
                institutionCode, displayName, hubLegalEntityCode, hubInstitutionCode, active);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ThinProxyInstitution that)) {
            return false;
        }
        return institutionCode.equals(that.institutionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(institutionCode);
    }
}
