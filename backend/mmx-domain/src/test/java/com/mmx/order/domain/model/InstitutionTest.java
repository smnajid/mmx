package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidInstitutionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("fast")
class InstitutionTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    private static Institution onboardedBnp(CounterpartyAccounts accounts) {
        return Institution.onboardFromGrant(
                "BVL-01", "BNP", new HubInstitutionLink(LOC, "BNP"), PAR, accounts);
    }

    @Test
    void onboardFromGrant_derivesDisplayNameAndLinksHubInstitution() {
        Institution onboarded = onboardedBnp(CounterpartyAccounts.none());

        assertThat(onboarded.getDisplayName()).isEqualTo("BNP via LOC");
        assertThat(onboarded.getOwningLegalEntityCode()).isEqualTo(PAR);
        assertThat(onboarded.getHubLink()).contains(new HubInstitutionLink(LOC, "BNP"));
        assertThat(onboarded.isOnboarded()).isTrue();
        assertThat(onboarded.isClosedToNewBusiness()).isFalse();
        assertThat(onboarded.getVersion()).isEqualTo(1);
    }

    @Test
    void onboardFromGrant_requiresHubDisplayName() {
        assertThatThrownBy(
                        () -> Institution.onboardFromGrant(
                                "BVL-01", " ", new HubInstitutionLink(LOC, "BNP"), PAR, CounterpartyAccounts.none()))
                .isInstanceOf(InvalidInstitutionException.class);
    }

    @Test
    void createNative_hasNoHubLinkAndStartsOpenAtVersionOne() {
        Institution hsbc =
                Institution.createNative("HSBC-01", "HSBC", LOC, CounterpartyAccounts.of("LOC-HSBC-T", null));

        assertThat(hsbc.getHubLink()).isEmpty();
        assertThat(hsbc.isOnboarded()).isFalse();
        assertThat(hsbc.isActive()).isTrue();
        assertThat(hsbc.getCounterpartyAccounts().term()).contains("LOC-HSBC-T");
        assertThat(hsbc.getVersion()).isEqualTo(1);
    }

    @Test
    void offboard_closesToNewBusinessKeepsAccountsAndReportsChange() {
        Institution onboarded = onboardedBnp(CounterpartyAccounts.of("PAR-BNP-T", null));

        assertThat(onboarded.offboard()).isTrue();

        assertThat(onboarded.isClosedToNewBusiness()).isTrue();
        assertThat(onboarded.isActive()).isFalse();
        assertThat(onboarded.getCounterpartyAccounts().term()).contains("PAR-BNP-T");
        assertThat(onboarded.getVersion()).isEqualTo(2);
    }

    @Test
    void offboard_whenAlreadyOffboardedReportsNoChange() {
        Institution onboarded = onboardedBnp(CounterpartyAccounts.none());
        onboarded.offboard();

        assertThat(onboarded.offboard()).isFalse();
        assertThat(onboarded.getVersion()).isEqualTo(2);
    }

    @Test
    void reopen_reopensToNewBusinessAndReportsChangeOnlyWhenClosed() {
        Institution onboarded = onboardedBnp(CounterpartyAccounts.none());

        assertThat(onboarded.reopen()).isFalse();
        assertThat(onboarded.getVersion()).isEqualTo(1);

        onboarded.offboard();
        assertThat(onboarded.reopen()).isTrue();
        assertThat(onboarded.isClosedToNewBusiness()).isFalse();
        assertThat(onboarded.getVersion()).isEqualTo(3);
    }

    @Test
    void changeAccounts_reportsNoChangeWhenValuesAreEqual() {
        Institution hsbc = Institution.createNative("HSBC-01", "HSBC", LOC, CounterpartyAccounts.of("T", "OC"));

        assertThat(hsbc.changeAccounts(CounterpartyAccounts.of(" T ", "OC"))).isFalse();
        assertThat(hsbc.getVersion()).isEqualTo(1);
    }

    @Test
    void changeAccounts_storesNewValuesAndIncrementsVersion() {
        Institution hsbc = Institution.createNative("HSBC-01", "HSBC", LOC, CounterpartyAccounts.of("T", "OC"));

        assertThat(hsbc.changeAccounts(CounterpartyAccounts.of("T2", null))).isTrue();

        assertThat(hsbc.getCounterpartyAccounts()).isEqualTo(CounterpartyAccounts.of("T2", null));
        assertThat(hsbc.getVersion()).isEqualTo(2);
    }

    @Test
    void changeAccounts_isAllowedWhileClosedToNewBusiness() {
        Institution onboarded = onboardedBnp(CounterpartyAccounts.none());
        onboarded.offboard();

        assertThat(onboarded.changeAccounts(CounterpartyAccounts.of(null, "PAR-BNP-OC"))).isTrue();
        assertThat(onboarded.getCounterpartyAccounts().onCall()).contains("PAR-BNP-OC");
    }

    @Test
    void deactivateAndReactivate_flipClosedToNewBusinessForHubInstitution() {
        Institution hsbc = Institution.createNative("HSBC-01", "HSBC", LOC, CounterpartyAccounts.none());

        assertThat(hsbc.deactivate()).isTrue();
        assertThat(hsbc.isClosedToNewBusiness()).isTrue();
        assertThat(hsbc.deactivate()).isFalse();
        assertThat(hsbc.reactivate()).isTrue();
        assertThat(hsbc.isClosedToNewBusiness()).isFalse();
        assertThat(hsbc.getVersion()).isEqualTo(3);
    }
}
