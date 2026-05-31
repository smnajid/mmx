package com.mmx.order.application.port.in;

import com.mmx.order.application.termrate.TermRateAuditRow;

import java.time.LocalDate;
import java.util.List;

public interface ListTermRatesForDayUseCase {

    List<TermRateAuditRow> list(LocalDate tradingDate);
}
