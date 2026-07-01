package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.DuplicateLegalEntityCodeException;
import com.mmx.order.domain.exception.InvalidLegalEntityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LegalEntity tenancy invariants")
class LegalEntityTenancyTest {

    private LegalEntityRegistry registry;
    private Organisation lodh;
    private Organisation hsbc;

    @BeforeEach
    void setUp() {
        registry = new LegalEntityRegistry();
        lodh = new Organisation(new OrganisationCode("LODH"));
        hsbc = new Organisation(new OrganisationCode("HSBC"));
    }

    @Test
    void legalEntity_belongsToOneOrganisation() {
        LegalEntity par = lodh.createTradingHub(new LegalEntityCode("PAR"), registry);

        assertThat(par.getOrganisationCode()).isEqualTo(new OrganisationCode("LODH"));
        assertThat(par.getCode()).isEqualTo(new LegalEntityCode("PAR"));
    }

    @Test
    void legalEntityCode_isGloballyUniqueAcrossOrganisations() {
        lodh.createTradingHub(new LegalEntityCode("PAR"), registry);

        assertThatThrownBy(() -> hsbc.createTradingHub(new LegalEntityCode("PAR"), registry))
                .isInstanceOf(DuplicateLegalEntityCodeException.class);
    }

    @Test
    void tradingHubRole_isMutuallyExclusiveWithTradingClient() {
        LegalEntity loc = lodh.createTradingHub(new LegalEntityCode("LOC"), registry);

        assertThat(loc.isTradingHub()).isTrue();
        assertThat(loc.isTradingClient()).isFalse();
        assertThat(loc.getRole()).isInstanceOf(TradingHubRole.class);
    }

    @Test
    void tradingClientRole_isMutuallyExclusiveWithTradingHub() {
        LegalEntity loc = lodh.createTradingHub(new LegalEntityCode("LOC"), registry);
        LegalEntity par = lodh.createTradingClient(new LegalEntityCode("PAR"), loc, registry);

        assertThat(par.isTradingClient()).isTrue();
        assertThat(par.isTradingHub()).isFalse();
        assertThat(par.getRole()).isInstanceOf(TradingClientRole.class);
        assertThat(((TradingClientRole) par.getRole()).connectedHubCode()).isEqualTo(new LegalEntityCode("LOC"));
    }

    @Test
    void tradingClient_mustConnectToSameOrganisationHub() {
        LegalEntity loc = lodh.createTradingHub(new LegalEntityCode("LOC"), registry);
        LegalEntity par = lodh.createTradingClient(new LegalEntityCode("PAR"), loc, registry);

        assertThat(((TradingClientRole) par.getRole()).connectedHubCode()).isEqualTo(loc.getCode());
        assertThat(par.getOrganisationCode()).isEqualTo(loc.getOrganisationCode());
    }

    @Test
    void crossOrganisationHubConnection_isRejected() {
        LegalEntity hsbcHub = hsbc.createTradingHub(new LegalEntityCode("HUB"), registry);

        assertThatThrownBy(() -> lodh.createTradingClient(new LegalEntityCode("PAR"), hsbcHub, registry))
                .isInstanceOf(InvalidLegalEntityException.class)
                .hasMessageContaining("same Organisation");
    }

    @Test
    void tradingClient_cannotConnectToAnotherClient() {
        LegalEntity loc = lodh.createTradingHub(new LegalEntityCode("LOC"), registry);
        LegalEntity par = lodh.createTradingClient(new LegalEntityCode("PAR"), loc, registry);

        assertThatThrownBy(() -> lodh.createTradingClient(new LegalEntityCode("SIN"), par, registry))
                .isInstanceOf(InvalidLegalEntityException.class)
                .hasMessageContaining("TradingHub");
    }
}
