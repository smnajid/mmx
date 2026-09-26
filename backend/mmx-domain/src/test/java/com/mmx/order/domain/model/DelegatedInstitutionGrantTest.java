package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidDelegatedGrantException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@Tag("fast")

class DelegatedInstitutionGrantTest {

    private static DelegatedInstitutionGrant grant() {
        return new DelegatedInstitutionGrant(
                "BNP",
                new LegalEntityCode("PAR"),
                "EUR",
                EnumSet.of(Tenor._1M, Tenor._3M),
                EnumSet.of(NoticePeriod._24H),
                true);
    }

    @Test
    void create_preservesKeyAndEnabledSets_andDefaultsActive() {
        DelegatedInstitutionGrant g = grant();

        assertThat(g.getHubInstitutionCode()).isEqualTo("BNP");
        assertThat(g.getClientLegalEntityCode()).isEqualTo(new LegalEntityCode("PAR"));
        assertThat(g.getCurrency()).isEqualTo("EUR");
        assertThat(g.getEnabledTenors()).containsExactly(Tenor._1M, Tenor._3M);
        assertThat(g.getEnabledNoticePeriods()).containsExactly(NoticePeriod._24H);
        assertThat(g.isActive()).isTrue();
    }

    @Test
    void key_isTupleOfHubInstitutionClientAndCurrency() {
        DelegatedInstitutionGrant g = grant();

        assertThat(g.key())
                .isEqualTo(new DelegatedGrantKey("BNP", new LegalEntityCode("PAR"), "EUR"));
    }

    @Test
    void twoGrantsWithSameTuple_haveEqualKeys() {
        DelegatedInstitutionGrant g1 = grant();
        DelegatedInstitutionGrant g2 =
                new DelegatedInstitutionGrant(
                        "BNP",
                        new LegalEntityCode("PAR"),
                        "EUR",
                        EnumSet.of(Tenor._6M),
                        EnumSet.noneOf(NoticePeriod.class),
                        false);

        assertThat(g1.key()).isEqualTo(g2.key());
        assertThat(g1.key().hashCode()).isEqualTo(g2.key().hashCode());
    }

    @Test
    void deactivate_togglesActiveFalseWithoutHardDelete() {
        DelegatedInstitutionGrant g = grant();

        DelegatedInstitutionGrant deactivated = g.withActive(false);

        assertThat(deactivated.isActive()).isFalse();
        assertThat(deactivated.key()).isEqualTo(g.key());
        assertThat(g.isActive()).isTrue();
    }

    @Test
    void reactivate_togglesActiveTrue() {
        DelegatedInstitutionGrant deactivated = grant().withActive(false);

        assertThat(deactivated.withActive(true).isActive()).isTrue();
    }

    @Test
    void updateEnabledSets_replacesTenorsAndNotices() {
        DelegatedInstitutionGrant g = grant();

        DelegatedInstitutionGrant updated =
                g.withEnabledSets(EnumSet.of(Tenor._6M), EnumSet.of(NoticePeriod._48H));

        assertThat(updated.getEnabledTenors()).containsExactly(Tenor._6M);
        assertThat(updated.getEnabledNoticePeriods()).containsExactly(NoticePeriod._48H);
        assertThat(updated.key()).isEqualTo(g.key());
        assertThat(updated.isActive()).isTrue();
    }

    @Test
    void blankHubInstitutionCode_rejected() {
        assertThatThrownBy(
                        () ->
                                new DelegatedInstitutionGrant(
                                        " ",
                                        new LegalEntityCode("PAR"),
                                        "EUR",
                                        EnumSet.of(Tenor._1M),
                                        EnumSet.noneOf(NoticePeriod.class),
                                        true))
                .isInstanceOf(InvalidDelegatedGrantException.class)
                .hasMessageContaining("hubInstitutionCode");
    }

    @Test
    void invalidCurrencyCode_rejected() {
        assertThatThrownBy(
                        () ->
                                new DelegatedInstitutionGrant(
                                        "BNP",
                                        new LegalEntityCode("PAR"),
                                        "euros",
                                        EnumSet.of(Tenor._1M),
                                        EnumSet.noneOf(NoticePeriod.class),
                                        true))
                .isInstanceOf(InvalidDelegatedGrantException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void nullEnabledSets_rejected() {
        assertThatThrownBy(
                        () ->
                                new DelegatedInstitutionGrant(
                                        "BNP",
                                        new LegalEntityCode("PAR"),
                                        "EUR",
                                        null,
                                        EnumSet.noneOf(NoticePeriod.class),
                                        true))
                .isInstanceOf(InvalidDelegatedGrantException.class);
    }
}
