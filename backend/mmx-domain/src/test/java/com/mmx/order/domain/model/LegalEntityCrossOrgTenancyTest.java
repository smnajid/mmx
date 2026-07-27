package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidLegalEntityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cross-organisation TradingClient tenancy — a TradingClient may connect to a TradingHub in a
 * different Organisation (cross-org routing). The same-org guard relaxes symmetrically; local vs
 * remote is derived from org-code comparison, not a stored flag.
 *
 * <p>Spec: {@code legal-entity-tenancy} — TradingClient membership spans organisations.
 */
@DisplayName("Cross-organisation TradingClient tenancy")
class LegalEntityCrossOrgTenancyTest {

    private LegalEntityRegistry registry;
    private Organisation lodh;
    private Organisation cged;
    private LegalEntity loc;

    @BeforeEach
    void setUp() {
        registry = new LegalEntityRegistry();
        lodh = new Organisation(new OrganisationCode("LODH"));
        cged = new Organisation(new OrganisationCode("CGED"));
        loc = lodh.createTradingHub(new LegalEntityCode("LOC"), registry);
    }

    @Test
    void tradingClient_mayConnectToHubInDifferentOrganisation() {
        LegalEntity cgd = cged.createTradingClient(new LegalEntityCode("CGD"), loc, registry);

        assertThat(cgd.isTradingClient()).isTrue();
        assertThat(((TradingClientRole) cgd.getRole()).connectedHubCode()).isEqualTo(new LegalEntityCode("LOC"));
        assertThat(cgd.getOrganisationCode()).isEqualTo(new OrganisationCode("CGED"));
        assertThat(loc.getOrganisationCode()).isEqualTo(new OrganisationCode("LODH"));
    }

    @Test
    void sameOrganisationConnection_remainsValid() {
        LegalEntity par = lodh.createTradingClient(new LegalEntityCode("PAR"), loc, registry);

        assertThat(par.isTradingClient()).isTrue();
        assertThat(((TradingClientRole) par.getRole()).connectedHubCode()).isEqualTo(new LegalEntityCode("LOC"));
        assertThat(par.getOrganisationCode()).isEqualTo(loc.getOrganisationCode());
    }

    @Test
    void tradingClient_stillMustConnectToATradingHub_notAnotherClient() {
        LegalEntity par = lodh.createTradingClient(new LegalEntityCode("PAR"), loc, registry);

        assertThatThrownBy(() -> lodh.createTradingClient(new LegalEntityCode("SIN"), par, registry))
                .isInstanceOf(InvalidLegalEntityException.class)
                .hasMessageContaining("TradingHub");
    }

    @Test
    void hubLocality_isDerivedFromOrganisationCodeComparison() {
        LegalEntity cgd = cged.createTradingClient(new LegalEntityCode("CGD"), loc, registry);
        LegalEntity par = lodh.createTradingClient(new LegalEntityCode("PAR"), loc, registry);

        // CGD's deployment org (CGED) ≠ connected hub's org (LODH) → REMOTE
        assertThat(cgd.hubLocality(cged.getCode(), loc.getOrganisationCode())).isEqualTo(HubLocality.REMOTE);
        // PAR's deployment org (LODH) = connected hub's org (LODH) → LOCAL
        assertThat(par.hubLocality(lodh.getCode(), loc.getOrganisationCode())).isEqualTo(HubLocality.LOCAL);
    }

    @Test
    void isLocalHub_and_isRemoteHub_deriveFromOrgCodeNotAStoredFlag() {
        LegalEntity cgd = cged.createTradingClient(new LegalEntityCode("CGD"), loc, registry);
        LegalEntity par = lodh.createTradingClient(new LegalEntityCode("PAR"), loc, registry);

        assertThat(cgd.isLocalHub(cged.getCode(), loc.getOrganisationCode())).isFalse();
        assertThat(cgd.isRemoteHub(cged.getCode(), loc.getOrganisationCode())).isTrue();

        assertThat(par.isLocalHub(lodh.getCode(), loc.getOrganisationCode())).isTrue();
        assertThat(par.isRemoteHub(lodh.getCode(), loc.getOrganisationCode())).isFalse();
    }

    @Test
    void hubLocality_usesDeploymentOrganisation_notTheEntitysOwnOrganisation() {
        LegalEntity cgd = cged.createTradingClient(new LegalEntityCode("CGD"), loc, registry);

        // Spec: locality compares the connected hub's org to THE DEPLOYMENT'S OWN org, not to this
        // entity's own organisation. CGD's own org is CGED and it connects to LOC (LODH). Supplying
        // different deployment orgs must yield different localities — proving the deployment org is
        // authoritative and the entity's org is NOT silently substituted (a foreign-org hub holding
        // CGD in its client list must not reuse CGD's org as its deployment org).
        assertThat(cgd.hubLocality(new OrganisationCode("CGED"), loc.getOrganisationCode()))
                .isEqualTo(HubLocality.REMOTE); // deployment CGED vs hub LODH
        assertThat(cgd.hubLocality(new OrganisationCode("LODH"), loc.getOrganisationCode()))
                .isEqualTo(HubLocality.LOCAL); // deployment LODH vs hub LODH
    }

    @Test
    void hubLocality_isMeaningfulForTradingClientsOnly() {
        assertThatThrownBy(() -> loc.isRemoteHub(lodh.getCode(), lodh.getCode()))
                .isInstanceOf(IllegalStateException.class);
    }
}
