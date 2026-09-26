package com.mmx.order.domain.model;

import java.util.Objects;

/**
 * Derived locality of a TradingClient's connection to its TradingHub. Computed by comparing the
 * connected hub's {@link OrganisationCode} to the deployment's own — never a stored flag.
 *
 * <p>Spec: {@code legal-entity-tenancy} — local vs remote is derived from whether the connected
 * hub's OrganisationCode matches the deployment's own.
 */
public enum HubLocality {
    /** Connected hub is in the same Organisation as the deployment — synchronous, in-process routing. */
    LOCAL,
    /** Connected hub is in a different Organisation — cross-deployment, eventually-consistent routing. */
    REMOTE;

    public static HubLocality of(OrganisationCode deploymentOrganisation, OrganisationCode connectedHubOrganisation) {
        Objects.requireNonNull(deploymentOrganisation, "deploymentOrganisation must not be null");
        Objects.requireNonNull(connectedHubOrganisation, "connectedHubOrganisation must not be null");
        return deploymentOrganisation.equals(connectedHubOrganisation) ? LOCAL : REMOTE;
    }

    public boolean isLocal() {
        return this == LOCAL;
    }

    public boolean isRemote() {
        return this == REMOTE;
    }
}
