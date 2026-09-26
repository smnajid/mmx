package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidInstitutionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("fast")
class EffectiveEnablementTest {

    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    private static DelegatedInstitutionGrant grant(Set<Tenor> tenors, Set<NoticePeriod> notices, boolean active) {
        return new DelegatedInstitutionGrant("BNP", PAR, "EUR", tenors, notices, active);
    }

    private static ClientEnablement client(Set<Tenor> tenors, Set<NoticePeriod> notices) {
        return new ClientEnablement("BVL-01", "EUR", tenors, notices);
    }

    @Test
    void effectiveSetIsGrantIntersectClientEnablement() {
        EffectiveEnablement effective =
                EffectiveEnablement.of(
                        Optional.of(grant(Set.of(Tenor._1M, Tenor._3M), Set.of(NoticePeriod._24H), true)),
                        client(Set.of(Tenor._3M, Tenor._6M), Set.of(NoticePeriod._24H, NoticePeriod._48H)));

        assertThat(effective.tenors()).containsExactly(Tenor._3M);
        assertThat(effective.noticePeriods()).containsExactly(NoticePeriod._24H);
        assertThat(effective.permits(Tenor._3M)).isTrue();
        assertThat(effective.permits(Tenor._1M)).isFalse();
        assertThat(effective.permits(NoticePeriod._48H)).isFalse();
    }

    @Test
    void emptyClientEnablementYieldsEmptyEffectiveSet() {
        EffectiveEnablement effective =
                EffectiveEnablement.of(
                        Optional.of(grant(Set.of(Tenor._1M), Set.of(NoticePeriod._24H), true)),
                        ClientEnablement.empty("BVL-01", "EUR"));

        assertThat(effective.tenors()).isEmpty();
        assertThat(effective.noticePeriods()).isEmpty();
    }

    @Test
    void inactiveOrMissingGrantYieldsEmptyEffectiveSet() {
        ClientEnablement enabled = client(Set.of(Tenor._1M), Set.of(NoticePeriod._24H));

        assertThat(EffectiveEnablement.of(Optional.of(grant(Set.of(Tenor._1M), Set.of(NoticePeriod._24H), false)), enabled)
                        .tenors())
                .isEmpty();
        assertThat(EffectiveEnablement.of(Optional.empty(), enabled).noticePeriods()).isEmpty();
    }

    @Test
    void clientEnabledButNotGrantedIsReported() {
        EffectiveEnablement effective =
                EffectiveEnablement.of(
                        Optional.of(grant(Set.of(Tenor._1M), Set.of(), true)),
                        client(Set.of(Tenor._1M, Tenor._3M), Set.of(NoticePeriod._48H)));

        assertThat(effective.enabledNotGrantedTenors()).containsExactly(Tenor._3M);
        assertThat(effective.enabledNotGrantedNoticePeriods()).containsExactly(NoticePeriod._48H);
    }

    @Test
    void enablingOutsideTheCurrentGrantIsRejected() {
        ClientEnablement current = ClientEnablement.empty("BVL-01", "EUR");
        Optional<DelegatedInstitutionGrant> grant =
                Optional.of(grant(Set.of(Tenor._1M, Tenor._3M), Set.of(NoticePeriod._24H), true));

        assertThatThrownBy(() -> current.replaceWith(Set.of(Tenor._6M), Set.of(), grant))
                .isInstanceOf(InvalidInstitutionException.class)
                .hasMessageContaining("6M");
        assertThatThrownBy(() -> current.replaceWith(Set.of(), Set.of(NoticePeriod._48H), grant))
                .isInstanceOf(InvalidInstitutionException.class)
                .hasMessageContaining("48H");
    }

    @Test
    void enablingOnAnInactiveGrantIsRejected() {
        ClientEnablement current = ClientEnablement.empty("BVL-01", "EUR");

        assertThatThrownBy(
                        () -> current.replaceWith(
                                Set.of(Tenor._1M), Set.of(), Optional.of(grant(Set.of(Tenor._1M), Set.of(), false))))
                .isInstanceOf(InvalidInstitutionException.class);
        assertThatThrownBy(() -> current.replaceWith(Set.of(Tenor._1M), Set.of(), Optional.empty()))
                .isInstanceOf(InvalidInstitutionException.class);
    }

    @Test
    void enablingWithinTheGrantReplacesTheSets() {
        ClientEnablement replaced =
                client(Set.of(Tenor._1M), Set.of())
                        .replaceWith(
                                Set.of(Tenor._3M),
                                Set.of(NoticePeriod._24H),
                                Optional.of(grant(Set.of(Tenor._1M, Tenor._3M), Set.of(NoticePeriod._24H), true)));

        assertThat(replaced.tenors()).containsExactly(Tenor._3M);
        assertThat(replaced.noticePeriods()).containsExactly(NoticePeriod._24H);
    }

    @Test
    void keepingAnEnabledButNoLongerGrantedTenorIsNotANewEnablement() {
        ClientEnablement current = client(Set.of(Tenor._1M, Tenor._3M), Set.of());
        Optional<DelegatedInstitutionGrant> reduced = Optional.of(grant(Set.of(Tenor._1M), Set.of(), true));

        ClientEnablement replaced = current.replaceWith(Set.of(Tenor._3M), Set.of(), reduced);

        assertThat(replaced.tenors()).containsExactly(Tenor._3M);
    }

    @Test
    void grantExpansionDoesNotWidenEffectiveSet() {
        ClientEnablement enabled = client(Set.of(Tenor._1M), Set.of());

        EffectiveEnablement expanded =
                EffectiveEnablement.of(Optional.of(grant(Set.of(Tenor._1M, Tenor._6M), Set.of(), true)), enabled);

        assertThat(expanded.permits(Tenor._6M)).isFalse();
    }
}
