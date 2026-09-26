package com.mmx.order.application.termrate;

import java.time.LocalDate;
import java.util.List;

public record TermRateCsvParseResult(LocalDate tradingDate, List<ParsedTermRateRow> rows) {}
