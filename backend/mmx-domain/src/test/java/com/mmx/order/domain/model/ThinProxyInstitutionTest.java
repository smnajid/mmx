package com.mmx.order.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThinProxyInstitutionTest {

    @Test
    void derivedDisplayName_isHubDisplayNameViaHubLegalEntityCode() {
        ThinProxyInstitution proxy =
                ThinProxyInstitution.forHubInstitution(
                        "BNPLOC",
                        new Institution("BNP", "BNP", true),
                        new LegalEntityCode("LOC"));

        assertThat(proxy.getDisplayName()).isEqualTo("BNP via LOC");
    }

    @Test
    void derivedDisplayName_isDeterministicForSameInputs() {
        ThinProxyInstitution a =
                ThinProxyInstitution.forHubInstitution(
                        "BNPLOC",
                        new Institution("BNP", "BNP", true),
                        new LegalEntityCode("LOC"));
        ThinProxyInstitution b =
                ThinProxyInstitution.forHubInstitution(
                        "BNPLOC2",
                        new Institution("BNP", "BNP", true),
                        new LegalEntityCode("LOC"));

        assertThat(a.getDisplayName()).isEqualTo(b.getDisplayName());
    }

    @Test
    void proxy_referencesHubInstitutionAndLegalEntity() {
        ThinProxyInstitution proxy =
                ThinProxyInstitution.forHubInstitution(
                        "BNPLOC",
                        new Institution("BNP", "BNP", true),
                        new LegalEntityCode("LOC"));

        assertThat(proxy.getHubLegalEntityCode()).isEqualTo(new LegalEntityCode("LOC"));
        assertThat(proxy.getHubInstitutionCode()).isEqualTo("BNP");
        assertThat(proxy.isProxy()).isTrue();
    }

    @Test
    void proxy_isActiveByDefault() {
        ThinProxyInstitution proxy =
                ThinProxyInstitution.forHubInstitution(
                        "BNPLOC",
                        new Institution("BNP", "BNP", true),
                        new LegalEntityCode("LOC"));

        assertThat(proxy.isActive()).isTrue();
    }

    @Test
    void proxy_carriesNoOwnRateCurves_itDelegatesToHubRates() {
        ThinProxyInstitution proxy =
                ThinProxyInstitution.forHubInstitution(
                        "BNPLOC",
                        new Institution("BNP", "BNP", true),
                        new LegalEntityCode("LOC"));

        // A proxy never holds its own curves: rate lookups must be resolved against the hub
        // institution via (hubLegalEntityCode, hubInstitutionCode). The proxy exposes no curve state.
        assertThat(proxy.hubRateLookupInstitutionCode()).isEqualTo("BNP");
        assertThat(proxy.hubRateLookupLegalEntityCode()).isEqualTo(new LegalEntityCode("LOC"));
    }
}
