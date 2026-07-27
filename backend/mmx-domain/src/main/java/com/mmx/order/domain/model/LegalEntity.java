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
     * Derived locality of this TradingClient's hub connection relative to the <em>calling</em>
     * deployment. Spec: {@code legal-entity-tenancy} — local vs remote is derived by comparing the
     * connected hub's {@link OrganisationCode} to <strong>the deployment's own</strong>, not by
     * reading a stored flag and not by reusing this entity's own organisation.
     *
     * <p>The deployment organisation is taken as an explicit argument rather than read from
     * {@code this}, because a {@code LegalEntity} may be a foreign-org member of this deployment's
     * hub (hub-owned membership spans organisations). The locality question is always answered from
     * the calling deployment's perspective, which only the caller knows — silently substituting the
     * entity's own organisation would give the wrong answer for a foreign-org client held in this
     * deployment's hub.
     *
     * @param deploymentOrganisation the calling deployment's own organisation
     * @param connectedHubOrganisation the organisation of the hub this TradingClient connects to
     * @throws IllegalStateException if this LegalEntity is not a TradingClient (a hub has no
     *     hub-of-its-own whose locality could be derived)
     */
    public HubLocality hubLocality(
            OrganisationCode deploymentOrganisation, OrganisationCode connectedHubOrganisation) {
        requireTradingClient("hubLocality");
        return HubLocality.of(deploymentOrganisation, connectedHubOrganisation);
    }

    public boolean isLocalHub(
            OrganisationCode deploymentOrganisation, OrganisationCode connectedHubOrganisation) {
        requireTradingClient("isLocalHub");
        return HubLocality.of(deploymentOrganisation, connectedHubOrganisation).isLocal();
    }

    public boolean isRemoteHub(
            OrganisationCode deploymentOrganisation, OrganisationCode connectedHubOrganisation) {
        requireTradingClient("isRemoteHub");
        return HubLocality.of(deploymentOrganisation, connectedHubOrganisation).isRemote();
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
