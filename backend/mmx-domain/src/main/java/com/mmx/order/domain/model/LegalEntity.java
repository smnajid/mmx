package com.mmx.order.domain.model;

import java.util.Objects;

public final class LegalEntity {

    private final LegalEntityCode code;
    private final OrganisationCode organisationCode;
    private final LegalEntityRole role;

    private LegalEntity(LegalEntityCode code, OrganisationCode organisationCode, LegalEntityRole role) {
        this.code = Objects.requireNonNull(code);
        this.organisationCode = Objects.requireNonNull(organisationCode);
        this.role = Objects.requireNonNull(role);
    }

    public static LegalEntity tradingHub(LegalEntityCode code, OrganisationCode organisationCode) {
        return new LegalEntity(code, organisationCode, new TradingHubRole());
    }

    public static LegalEntity tradingClient(
            LegalEntityCode code, OrganisationCode organisationCode, LegalEntity connectedHub) {
        Objects.requireNonNull(connectedHub, "connectedHub must not be null");
        if (!(connectedHub.role instanceof TradingHubRole)) {
            throw new com.mmx.order.domain.exception.InvalidLegalEntityException(
                    "TradingClient must connect to a TradingHub");
        }
        if (!connectedHub.organisationCode.equals(organisationCode)) {
            throw new com.mmx.order.domain.exception.InvalidLegalEntityException(
                    "TradingClient hub connection must be within the same Organisation");
        }
        return new LegalEntity(code, organisationCode, new TradingClientRole(connectedHub.code));
    }

    public LegalEntityCode getCode() {
        return code;
    }

    public OrganisationCode getOrganisationCode() {
        return organisationCode;
    }

    public LegalEntityRole getRole() {
        return role;
    }

    public boolean isTradingHub() {
        return role instanceof TradingHubRole;
    }

    public boolean isTradingClient() {
        return role instanceof TradingClientRole;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LegalEntity that)) {
            return false;
        }
        return code.equals(that.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }
}
