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
        assertThat(cgd.hubLocality(loc.getOrganisationCode())).isEqualTo(HubLocality.REMOTE);
        // PAR's deployment org (LODH) = connected hub's org (LODH) → LOCAL
        assertThat(par.hubLocality(loc.getOrganisationCode())).isEqualTo(HubLocality.LOCAL);
    }

    @Test
    void isLocalHub_and_isRemoteHub_deriveFromOrgCodeNotAStoredFlag() {
        LegalEntity cgd = cged.createTradingClient(new LegalEntityCode("CGD"), loc, registry);
        LegalEntity par = lodh.createTradingClient(new LegalEntityCode("PAR"), loc, registry);

        assertThat(cgd.isLocalHub(loc.getOrganisationCode())).isFalse();
        assertThat(cgd.isRemoteHub(loc.getOrganisationCode())).isTrue();

        assertThat(par.isLocalHub(loc.getOrganisationCode())).isTrue();
        assertThat(par.isRemoteHub(loc.getOrganisationCode())).isFalse();
    }

    @Test
    void hubLocality_isMeaningfulForTradingClientsOnly() {
        assertThatThrownBy(() -> loc.isRemoteHub(lodh.getCode()))
                .isInstanceOf(IllegalStateException.class);
    }
}
