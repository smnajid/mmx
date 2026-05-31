package com.mmx.order.application.termrate;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TermRateCsvParserTest {

    private final TermRateCsvParser parser = new TermRateCsvParser();

    @Test
    void parse_validFile_returnsRowsAndTradingDate() {
        byte[] csv =
                """
                tradingDate,institutionCode,currency,tenor,rate
                2026-05-30,HSBC-01,EUR,1M,3.25000000
                2026-05-30,BCI-01,USD,1W,4.12000000
                """
                        .getBytes(StandardCharsets.UTF_8);

        TermRateCsvParseResult result = parser.parse(csv);

        assertThat(result.tradingDate()).isEqualTo(LocalDate.of(2026, 5, 30));
        assertThat(result.rows()).hasSize(2);
    }

    @Test
    void parse_rejectsMissingColumn() {
        byte[] csv =
                """
                tradingDate,institutionCode,currency,rate
                2026-05-30,HSBC-01,EUR,3.25000000
                """
                        .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(csv))
                .isInstanceOf(TermRateCsvStructuralException.class)
                .hasMessageContaining("tenor");
    }

    @Test
    void parse_rejectsMixedTradingDates() {
        byte[] csv =
                """
                tradingDate,institutionCode,currency,tenor,rate
                2026-05-30,HSBC-01,EUR,1M,3.25000000
                2026-05-31,HSBC-01,EUR,3M,3.41000000
                """
                        .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(csv))
                .isInstanceOf(TermRateCsvStructuralException.class)
                .hasMessageContaining("same tradingDate");
    }

    @Test
    void parse_rejectsDuplicateKeys() {
        byte[] csv =
                """
                tradingDate,institutionCode,currency,tenor,rate
                2026-05-30,HSBC-01,EUR,1M,3.25000000
                2026-05-30,HSBC-01,EUR,1M,3.50000000
                """
                        .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(csv))
                .isInstanceOf(TermRateCsvStructuralException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void parse_rejectsHeaderOnly() {
        byte[] csv = "tradingDate,institutionCode,currency,tenor,rate\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(csv))
                .isInstanceOf(TermRateCsvStructuralException.class)
                .hasMessageContaining("no data rows");
    }

    @Test
    void parse_rejectsBadDateFormat() {
        byte[] csv =
                """
                tradingDate,institutionCode,currency,tenor,rate
                not-a-date,HSBC-01,EUR,1M,3.25000000
                """
                        .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(csv))
                .isInstanceOf(TermRateCsvStructuralException.class)
                .hasMessageContaining("tradingDate");
    }
}
