package com.mmx.order.domain.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class DelegatedGrantProspectiveTest {

    @Test
    void editingGrant_doesNotChangePreviouslyCapturedSnapshot() {
        DelegatedInstitutionGrant grant =
                new DelegatedInstitutionGrant(
                        "BNP",
                        new LegalEntityCode("PAR"),
                        "EUR",
                        EnumSet.of(Tenor._1M, Tenor._3M),
                        EnumSet.noneOf(NoticePeriod.class),
                        true);

        ResolvedGrant snapshotAtRouting = ResolvedGrant.from(grant);

        DelegatedInstitutionGrant reduced =
                grant.withEnabledSets(EnumSet.of(Tenor._1M), EnumSet.noneOf(NoticePeriod.class));

        assertThat(reduced.getEnabledTenors()).containsExactly(Tenor._1M);
        assertThat(snapshotAtRouting.enabledTenors()).containsExactly(Tenor._1M, Tenor._3M);
        assertThat(snapshotAtRouting.active()).isTrue();
    }

    @Test
    void deactivatingGrant_doesNotChangePreviouslyCapturedSnapshot() {
        DelegatedInstitutionGrant grant =
                new DelegatedInstitutionGrant(
                        "BNP",
                        new LegalEntityCode("PAR"),
                        "EUR",
                        EnumSet.of(Tenor._3M),
                        EnumSet.noneOf(NoticePeriod.class),
                        true);

        ResolvedGrant snapshotAtRouting = ResolvedGrant.from(grant);

        DelegatedInstitutionGrant deactivated = grant.withActive(false);

        assertThat(deactivated.isActive()).isFalse();
        assertThat(snapshotAtRouting.active()).isTrue();
        assertThat(snapshotAtRouting.enabledTenors()).containsExactly(Tenor._3M);
    }

    @Test
    void capturedSnapshot_isImmutableAndViewOnly() {
        DelegatedInstitutionGrant grant =
                new DelegatedInstitutionGrant(
                        "BNP",
                        new LegalEntityCode("PAR"),
                        "EUR",
                        EnumSet.of(Tenor._3M),
                        EnumSet.of(NoticePeriod._24H),
                        true);

        ResolvedGrant snapshot = ResolvedGrant.from(grant);

        assertThat(snapshot.key()).isEqualTo(grant.key());
        assertThat(snapshot.enabledNoticePeriods()).containsExactly(NoticePeriod._24H);
        // Snapshot exposes no mutators; it is a read-only record of grant state at routing time.
        assertThat(snapshot.getClass().isRecord()).isTrue();
    }
}
