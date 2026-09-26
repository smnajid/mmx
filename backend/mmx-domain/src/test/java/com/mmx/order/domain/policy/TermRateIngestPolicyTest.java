package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.TermRateIngestException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TermRate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@Tag("fast")

class TermRateIngestPolicyTest {

    private final TermRateIngestPolicy policy = new TermRateIngestPolicy();

    private final Institution activeInstitution = new Institution("HSBC-01", "HSBC", true);
    private final Institution inactiveInstitution = new Institution("OLD-01", "Old Bank", false);
    private final ManagedCurrency eur =
            new ManagedCurrency(
                    "EUR",
                    true,
                    new BigDecimal("1000"),
                    new BigDecimal("500"),
                    EnumSet.of(Tenor._1M, Tenor._3M),
                    EnumSet.noneOf(com.mmx.order.domain.model.NoticePeriod.class));

    private TermRate sampleRate() {
        return new TermRate(LocalDate.of(2026, 5, 30), "HSBC-01", "EUR", Tenor._1M, new BigDecimal("3.25000000"));
    }

    @Test
    void parseTenorCode_acceptsKnownCode() {
        assertThat(policy.parseTenorCode(2, "3M")).isEqualTo(Tenor._3M);
    }

    @Test
    void parseTenorCode_rejectsUnknownCode() {
        assertThatThrownBy(() -> policy.parseTenorCode(2, "9M"))
                .isInstanceOf(TermRateIngestException.class)
                .satisfies(
                        ex -> {
                            TermRateIngestException err = (TermRateIngestException) ex;
                            assertThat(err.getLine()).isEqualTo(2);
                            assertThat(err.getField()).isEqualTo("tenor");
                        });
    }

    @Test
    void validateRow_rejectsUnknownInstitution() {
        assertThatThrownBy(
                        () ->
                                policy.validateRow(
                                        2, sampleRate(), Optional.empty(), Optional.of(eur)))
                .isInstanceOf(TermRateIngestException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void validateRow_rejectsInactiveInstitution() {
        assertThatThrownBy(
                        () ->
                                policy.validateRow(
                                        2,
                                        sampleRate(),
                                        Optional.of(inactiveInstitution),
                                        Optional.of(eur)))
                .isInstanceOf(TermRateIngestException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void validateRow_rejectsUnknownCurrency() {
        assertThatThrownBy(
                        () ->
                                policy.validateRow(
                                        2, sampleRate(), Optional.of(activeInstitution), Optional.empty()))
                .isInstanceOf(TermRateIngestException.class)
                .hasMessageContaining("not managed");
    }

    @Test
    void validateRow_rejectsInactiveCurrency() {
        ManagedCurrency inactive = eur.withActive(false);
        assertThatThrownBy(
                        () ->
                                policy.validateRow(
                                        2,
                                        sampleRate(),
                                        Optional.of(activeInstitution),
                                        Optional.of(inactive)))
                .isInstanceOf(TermRateIngestException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void validateRow_rejectsTenorNotEnabled() {
        TermRate row =
                new TermRate(
                        LocalDate.of(2026, 5, 30),
                        "HSBC-01",
                        "EUR",
                        Tenor._1W,
                        new BigDecimal("3.25000000"));
        assertThatThrownBy(
                        () ->
                                policy.validateRow(
                                        2, row, Optional.of(activeInstitution), Optional.of(eur)))
                .isInstanceOf(TermRateIngestException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void validateRow_rejectsNonPositiveRate() {
        TermRate row =
                new TermRate(LocalDate.of(2026, 5, 30), "HSBC-01", "EUR", Tenor._1M, BigDecimal.ZERO);
        assertThatThrownBy(
                        () ->
                                policy.validateRow(
                                        2, row, Optional.of(activeInstitution), Optional.of(eur)))
                .isInstanceOf(TermRateIngestException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void validateRow_rejectsRateWithTooManyFractionalDigits() {
        TermRate row =
                new TermRate(
                        LocalDate.of(2026, 5, 30),
                        "HSBC-01",
                        "EUR",
                        Tenor._1M,
                        new BigDecimal("3.123456789"));
        assertThatThrownBy(
                        () ->
                                policy.validateRow(
                                        2, row, Optional.of(activeInstitution), Optional.of(eur)))
                .isInstanceOf(TermRateIngestException.class)
                .hasMessageContaining("8 fractional digits");
    }

    @Test
    void validateRow_acceptsValidRow() {
        assertThatCode(
                        () ->
                                policy.validateRow(
                                        2,
                                        sampleRate(),
                                        Optional.of(activeInstitution),
                                        Optional.of(eur)))
                .doesNotThrowAnyException();
    }
}
