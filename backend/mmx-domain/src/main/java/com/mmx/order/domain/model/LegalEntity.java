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
        // The same-org guard is relaxed symmetrically: a TradingClient may point at a hub in a
        // different Organisation, and a hub's client list may include a foreign-org entity. The
        // hub connection itself is bidirectional, wired by the connection-registration task.
        // Local vs remote is DERIVED (HubLocality) from org-code comparison, not stored here.
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

    /**
     * Derived locality of this TradingClient's hub connection relative to a deployment's own
     * Organisation. Spec: {@code legal-entity-tenancy} — local vs remote is derived by comparing
     * the connected hub's OrganisationCode to the deployment's own, not by reading a stored flag.
     *
     * @throws IllegalStateException if this LegalEntity is not a TradingClient (a hub has no
     *     hub-of-its-own whose locality could be derived).
     */
    public HubLocality hubLocality(OrganisationCode connectedHubOrganisation) {
        requireTradingClient("hubLocality");
        return HubLocality.of(this.organisationCode, connectedHubOrganisation);
    }

    public boolean isLocalHub(OrganisationCode connectedHubOrganisation) {
        requireTradingClient("isLocalHub");
        return HubLocality.of(this.organisationCode, connectedHubOrganisation).isLocal();
    }

    public boolean isRemoteHub(OrganisationCode connectedHubOrganisation) {
        requireTradingClient("isRemoteHub");
        return HubLocality.of(this.organisationCode, connectedHubOrganisation).isRemote();
    }

    private void requireTradingClient(String operation) {
        if (!(role instanceof TradingClientRole)) {
            throw new IllegalStateException(
                    operation + " is only meaningful for a TradingClient (entity " + code + " is not a client)");
        }
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
