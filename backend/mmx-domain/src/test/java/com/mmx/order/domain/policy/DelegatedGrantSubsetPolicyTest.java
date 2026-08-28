package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.InvalidDelegatedGrantException;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
@Tag("fast")

class DelegatedGrantSubsetPolicyTest {

    private static final BigDecimal MIN_SUB = new BigDecimal("1000000.00");
    private static final BigDecimal MIN_LIFE = new BigDecimal("250000.00");

    private static ManagedCurrency hubCurrency(EnumSet<Tenor> tenors, EnumSet<NoticePeriod> notices) {
        return new ManagedCurrency("EUR", true, MIN_SUB, MIN_LIFE, tenors, notices);
    }

    @Test
    void tenorSubset_withinHubSet_accepted() {
        ManagedCurrency hub = hubCurrency(EnumSet.of(Tenor._1M, Tenor._3M, Tenor._6M), EnumSet.noneOf(NoticePeriod.class));

        assertThatCode(() ->
                        DelegatedGrantSubsetPolicy.validate(
                                EnumSet.of(Tenor._1M, Tenor._3M),
                                EnumSet.noneOf(NoticePeriod.class),
                                hub))
                .doesNotThrowAnyException();
    }

    @Test
    void noticeSubset_withinHubSet_accepted() {
        ManagedCurrency hub =
                hubCurrency(EnumSet.noneOf(Tenor.class), EnumSet.of(NoticePeriod._24H, NoticePeriod._48H));

        assertThatCode(() ->
                        DelegatedGrantSubsetPolicy.validate(
                                EnumSet.noneOf(Tenor.class),
                                EnumSet.of(NoticePeriod._24H),
                                hub))
                .doesNotThrowAnyException();
    }

    @Test
    void tenorOutsideHubSet_rejected() {
        ManagedCurrency hub = hubCurrency(EnumSet.of(Tenor._1M, Tenor._3M), EnumSet.noneOf(NoticePeriod.class));

        assertThatThrownBy(() ->
                        DelegatedGrantSubsetPolicy.validate(
                                EnumSet.of(Tenor._1M, Tenor._6M),
                                EnumSet.noneOf(NoticePeriod.class),
                                hub))
                .isInstanceOf(InvalidDelegatedGrantException.class)
                .hasMessageContaining("enabledTenors");
    }

    @Test
    void noticeOutsideHubSet_rejected() {
        ManagedCurrency hub =
                hubCurrency(EnumSet.noneOf(Tenor.class), EnumSet.of(NoticePeriod._24H));

        assertThatThrownBy(() ->
                        DelegatedGrantSubsetPolicy.validate(
                                EnumSet.noneOf(Tenor.class),
                                EnumSet.of(NoticePeriod._48H),
                                hub))
                .isInstanceOf(InvalidDelegatedGrantException.class)
                .hasMessageContaining("enabledNoticePeriods");
    }

    @Test
    void parseTenorCodes_invalidCode_rejected() {
        assertThatThrownBy(() -> DelegatedGrantSubsetPolicy.parseTenorCodes(List.of("1M", "9M")))
                .isInstanceOf(InvalidDelegatedGrantException.class)
                .hasMessageContaining("9M");
    }

    @Test
    void parseNoticeCodes_invalidCode_rejected() {
        assertThatThrownBy(() -> DelegatedGrantSubsetPolicy.parseNoticeCodes(List.of("72H")))
                .isInstanceOf(InvalidDelegatedGrantException.class)
                .hasMessageContaining("72H");
    }

    @Test
    void parseTenorCodes_validCodes_returnEnumSet() {
        assertThat(DelegatedGrantSubsetPolicy.parseTenorCodes(List.of("1M", "3M")))
                .containsExactly(Tenor._1M, Tenor._3M);
    }

    @Test
    void parseNoticeCodes_validCodes_returnEnumSet() {
        assertThat(DelegatedGrantSubsetPolicy.parseNoticeCodes(List.of("24H", "48H")))
                .containsExactly(NoticePeriod._24H, NoticePeriod._48H);
    }
}
