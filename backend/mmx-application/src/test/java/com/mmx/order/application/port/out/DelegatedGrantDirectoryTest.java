package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class DelegatedGrantDirectoryTest {

    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    private static FakeDirectory directory() {
        return new FakeDirectory();
    }

    @Test
    void grantedTenor_isConfirmed() {
        FakeDirectory directory = directory();
        directory.put("BNPLOC", PAR, "EUR", EnumSet.of(Tenor._3M), EnumSet.noneOf(NoticePeriod.class), true);

        assertThat(directory.resolveTenor(PAR, "BNPLOC", "EUR", Tenor._3M, OrderOperation.SUBSCRIPTION))
                .isEqualTo(GrantResolution.GRANTED);
    }

    @Test
    void tenorOutOfEnabledSet_isNotInEnabledSet() {
        FakeDirectory directory = directory();
        directory.put("BNPLOC", PAR, "EUR", EnumSet.of(Tenor._1M, Tenor._3M), EnumSet.noneOf(NoticePeriod.class), true);

        assertThat(directory.resolveTenor(PAR, "BNPLOC", "EUR", Tenor._6M, OrderOperation.SUBSCRIPTION))
                .isEqualTo(GrantResolution.NOT_IN_ENABLED_SET);
    }

    @Test
    void inactiveGrant_isNoActiveGrant() {
        FakeDirectory directory = directory();
        directory.put("BNPLOC", PAR, "EUR", EnumSet.of(Tenor._3M), EnumSet.noneOf(NoticePeriod.class), false);

        assertThat(directory.resolveTenor(PAR, "BNPLOC", "EUR", Tenor._3M, OrderOperation.SUBSCRIPTION))
                .isEqualTo(GrantResolution.NO_ACTIVE_GRANT);
    }

    @Test
    void missingGrant_isNoActiveGrant() {
        FakeDirectory directory = directory();

        assertThat(directory.resolveTenor(PAR, "BNPLOC", "USD", Tenor._3M, OrderOperation.INCREASE))
                .isEqualTo(GrantResolution.NO_ACTIVE_GRANT);
    }

    @Test
    void grantedNotice_isConfirmed() {
        FakeDirectory directory = directory();
        directory.put("BNPLOC", PAR, "EUR", EnumSet.noneOf(Tenor.class), EnumSet.of(NoticePeriod._24H), true);

        assertThat(directory.resolveNotice(PAR, "BNPLOC", "EUR", NoticePeriod._24H, OrderOperation.SUBSCRIPTION))
                .isEqualTo(GrantResolution.GRANTED);
    }

    @Test
    void redemptionOnAnInactiveGrant_isPermittedWithoutTheGrant() {
        FakeDirectory directory = directory();
        directory.put("BNPLOC", PAR, "EUR", EnumSet.noneOf(Tenor.class), EnumSet.of(NoticePeriod._24H), false);

        GrantResolution resolution =
                directory.resolveNotice(PAR, "BNPLOC", "EUR", NoticePeriod._24H, OrderOperation.REDEMPTION);

        assertThat(resolution).isEqualTo(GrantResolution.NOT_REQUIRED);
        assertThat(resolution.isPermitted()).isTrue();
    }

    @Test
    void decreaseOnAMissingGrant_isPermittedWithoutTheGrant() {
        FakeDirectory directory = directory();

        assertThat(directory.resolveTenor(PAR, "BNPLOC", "USD", Tenor._3M, OrderOperation.DECREASE).isPermitted())
                .isTrue();
    }

    @Test
    void increaseOnAnInactiveGrant_isNoActiveGrant() {
        FakeDirectory directory = directory();
        directory.put("BNPLOC", PAR, "EUR", EnumSet.noneOf(Tenor.class), EnumSet.of(NoticePeriod._24H), false);

        GrantResolution resolution =
                directory.resolveNotice(PAR, "BNPLOC", "EUR", NoticePeriod._24H, OrderOperation.INCREASE);

        assertThat(resolution).isEqualTo(GrantResolution.NO_ACTIVE_GRANT);
        assertThat(resolution.isPermitted()).isFalse();
    }

    @Test
    void grantedBooleanAccessor_isTrueOnlyForGranted() {
        FakeDirectory directory = directory();
        directory.put("BNPLOC", PAR, "EUR", EnumSet.of(Tenor._3M), EnumSet.noneOf(NoticePeriod.class), true);

        assertThat(GrantResolution.GRANTED.isGranted()).isTrue();
        assertThat(GrantResolution.NOT_IN_ENABLED_SET.isGranted()).isFalse();
        assertThat(GrantResolution.NO_ACTIVE_GRANT.isGranted()).isFalse();
        assertThat(GrantResolution.NOT_REQUIRED.isGranted()).isFalse();
        assertThat(GrantResolution.GRANTED.isPermitted()).isTrue();
        assertThat(GrantResolution.NOT_IN_ENABLED_SET.isPermitted()).isFalse();
    }

    /** Minimal fake of the grant lookups; the operation rule is the port's own. */
    private static final class FakeDirectory implements DelegatedGrantDirectory {

        private final Map<Key, Entry> grants = new HashMap<>();

        void put(String institutionCode, LegalEntityCode client, String currency,
                 EnumSet<Tenor> tenors, EnumSet<NoticePeriod> notices, boolean active) {
            grants.put(new Key(client, institutionCode, currency), new Entry(tenors, notices, active));
        }

        @Override
        public GrantResolution lookupTenor(LegalEntityCode client, String institutionCode, String currency, Tenor tenor) {
            Entry entry = grants.get(new Key(client, institutionCode, currency));
            if (entry == null || !entry.active) {
                return GrantResolution.NO_ACTIVE_GRANT;
            }
            return entry.tenors.contains(tenor) ? GrantResolution.GRANTED : GrantResolution.NOT_IN_ENABLED_SET;
        }

        @Override
        public GrantResolution lookupNotice(LegalEntityCode client, String institutionCode, String currency, NoticePeriod notice) {
            Entry entry = grants.get(new Key(client, institutionCode, currency));
            if (entry == null || !entry.active) {
                return GrantResolution.NO_ACTIVE_GRANT;
            }
            return entry.notices.contains(notice) ? GrantResolution.GRANTED : GrantResolution.NOT_IN_ENABLED_SET;
        }

        private record Key(LegalEntityCode client, String institutionCode, String currency) {}

        private record Entry(EnumSet<Tenor> tenors, EnumSet<NoticePeriod> notices, boolean active) {}
    }
}
