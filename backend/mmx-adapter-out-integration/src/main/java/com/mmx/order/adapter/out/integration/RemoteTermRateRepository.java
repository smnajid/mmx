package com.mmx.order.adapter.out.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.Tenor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Remote-backed {@link TermRateRepository}: reads term-rate snapshots for a trading date live from
 * LODH via REST. Read-only; CGED does not persist hub term-rate audit data.
 */
public final class RemoteTermRateRepository implements TermRateRepository {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RemoteReferenceDataContext ctx;

    public RemoteTermRateRepository(RemoteReferenceDataContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public List<TermRateAuditRow> findByTradingDate(LocalDate tradingDate) {
        String path = "/api/v1/cross-org/reference/term-rates?tradingDate="
                + tradingDate.format(ISO_DATE);
        return RemoteReferenceDataHttp.getList(ctx, path, TermRateDto.class).stream()
                .map(TermRateDto::toDomain)
                .toList();
    }

    @Override
    public List<LocalDate> findDistinctTradingDatesDesc() {
        return List.of();
    }

    @Override
    public List<TermRateAuditRow> findLatestRatePerInstitution(String currency, Tenor tenor) {
        return List.of();
    }

    @Override
    public List<String> findDistinctCurrenciesWithTermRates() {
        return List.of();
    }

    @Override
    public void replaceAllForDate(LocalDate tradingDate, List<TermRateAuditRow> rows) {
        throw new UnsupportedOperationException(
                "Remote-backed TermRateRepository is read-only; CGED does not master hub term-rate data");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class TermRateDto {
        @JsonProperty("tradingDate")
        String tradingDate;
        @JsonProperty("institutionCode")
        String institutionCode;
        @JsonProperty("currency")
        String currency;
        @JsonProperty("tenor")
        String tenor;
        @JsonProperty("rate")
        double rate;

        TermRateAuditRow toDomain() {
            return new TermRateAuditRow(
                    LocalDate.parse(tradingDate, ISO_DATE),
                    institutionCode,
                    currency,
                    Tenor.fromCode(tenor).orElse(null),
                    BigDecimal.valueOf(rate),
                    null,
                    null);
        }
    }
}
