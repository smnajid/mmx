package com.mmx.order.application.ordercreation;

import java.time.LocalDate;
import java.util.List;

public record TermCurrenciesResult(LocalDate tradingDate, List<String> currencies) {}
