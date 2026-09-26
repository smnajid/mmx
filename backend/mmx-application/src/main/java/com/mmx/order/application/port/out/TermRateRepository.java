package com.mmx.order.application.port.out;

import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.Tenor;

import java.time.LocalDate;
import java.util.List;

public interface TermRateRepository {

    void replaceAllForDate(LocalDate tradingDate, List<TermRateAuditRow> rows);

    List<TermRateAuditRow> findByTradingDate(LocalDate tradingDate);

    List<LocalDate> findDistinctTradingDatesDesc();

    List<TermRateAuditRow> findLatestRatePerInstitution(String currency, Tenor tenor);

    List<String> findDistinctCurrenciesWithTermRates();
}
