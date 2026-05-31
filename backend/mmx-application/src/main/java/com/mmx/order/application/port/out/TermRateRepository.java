package com.mmx.order.application.port.out;

import com.mmx.order.application.termrate.TermRateAuditRow;

import java.time.LocalDate;
import java.util.List;

public interface TermRateRepository {

    void replaceAllForDate(LocalDate tradingDate, List<TermRateAuditRow> rows);

    List<TermRateAuditRow> findByTradingDate(LocalDate tradingDate);

    List<LocalDate> findDistinctTradingDatesDesc();
}
