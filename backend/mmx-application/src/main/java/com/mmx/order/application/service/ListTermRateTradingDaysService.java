package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ListTermRateTradingDaysUseCase;
import com.mmx.order.application.port.out.TermRateRepository;

import java.time.LocalDate;
import java.util.List;

public final class ListTermRateTradingDaysService implements ListTermRateTradingDaysUseCase {

    private final TermRateRepository termRateRepository;

    public ListTermRateTradingDaysService(TermRateRepository termRateRepository) {
        this.termRateRepository = termRateRepository;
    }

    @Override
    public List<LocalDate> list() {
        return termRateRepository.findDistinctTradingDatesDesc();
    }
}
