package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidManagedCurrencyException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@Tag("fast")

class ManagedCurrencyTest {

    private static final BigDecimal MIN_SUB = new BigDecimal("1000000.00");
    private static final BigDecimal MIN_LIFE = new BigDecimal("250000.00");

    @Test
    void termOnly_withEmptyNotices_isValid() {
        ManagedCurrency currency =
                new ManagedCurrency(
                        "EUR",
                        true,
                        MIN_SUB,
                        MIN_LIFE,
                        EnumSet.of(Tenor._3M),
                        EnumSet.noneOf(NoticePeriod.class));

        assertThat(currency.getEnabledTenors()).containsExactly(Tenor._3M);
        assertThat(currency.getEnabledNoticePeriods()).isEmpty();
    }

    @Test
    void onCallOnly_withEmptyTenors_isValid() {
        ManagedCurrency currency =
                new ManagedCurrency(
                        "EUR",
                        true,
                        MIN_SUB,
                        MIN_LIFE,
                        EnumSet.noneOf(Tenor.class),
                        EnumSet.of(NoticePeriod._24H));

        assertThat(currency.getEnabledTenors()).isEmpty();
        assertThat(currency.getEnabledNoticePeriods()).containsExactly(NoticePeriod._24H);
    }

    @Test
    void bothWorkspacesEmpty_isRejected() {
        assertThatThrownBy(
                        () ->
                                new ManagedCurrency(
                                        "EUR",
                                        true,
                                        MIN_SUB,
                                        MIN_LIFE,
                                        EnumSet.noneOf(Tenor.class),
                                        EnumSet.noneOf(NoticePeriod.class)))
                .isInstanceOf(InvalidManagedCurrencyException.class)
                .hasMessageContaining("At least one workspace");
    }
}
