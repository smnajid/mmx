package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ListTermRatesForDayUseCase;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.termrate.TermRateAuditRow;

import java.time.LocalDate;
import java.util.List;

public final class ListTermRatesForDayService implements ListTermRatesForDayUseCase {

    private final TermRateRepository termRateRepository;

    public ListTermRatesForDayService(TermRateRepository termRateRepository) {
        this.termRateRepository = termRateRepository;
    }

    @Override
    public List<TermRateAuditRow> list(LocalDate tradingDate) {
        return termRateRepository.findByTradingDate(tradingDate);
    }
}
