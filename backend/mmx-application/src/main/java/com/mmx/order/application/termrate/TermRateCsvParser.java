package com.mmx.order.application.termrate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TermRateCsvParser {

    private static final List<String> REQUIRED_COLUMNS =
            List.of("tradingDate", "institutionCode", "currency", "tenor", "rate");

    public TermRateCsvParseResult parse(byte[] csvBytes) {
        String content = new String(csvBytes, StandardCharsets.UTF_8);
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new TermRateCsvStructuralException("CSV file is empty");
            }
            Map<String, Integer> columnIndex = parseHeader(headerLine);
            List<ParsedTermRateRow> rows = new ArrayList<>();
            String line;
            int lineNumber = 1;
            LocalDate tradingDate = null;
            Set<String> keys = new HashSet<>();
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                ParsedTermRateRow row = parseDataRow(lineNumber, line, columnIndex);
                if (tradingDate == null) {
                    tradingDate = row.tradingDate();
                } else if (!tradingDate.equals(row.tradingDate())) {
                    throw new TermRateCsvStructuralException(
                            "All rows must share the same tradingDate (mixed dates in file)");
                }
                String key =
                        row.institutionCode()
                                + "|"
                                + row.currency()
                                + "|"
                                + row.tenorCode();
                if (!keys.add(key)) {
                    throw new TermRateCsvStructuralException(
                            "Duplicate row for institution, currency, and tenor at line " + lineNumber);
                }
                rows.add(row);
            }
            if (rows.isEmpty()) {
                throw new TermRateCsvStructuralException("CSV file has no data rows");
            }
            return new TermRateCsvParseResult(tradingDate, List.copyOf(rows));
        } catch (IOException ex) {
            throw new TermRateCsvStructuralException("Failed to read CSV: " + ex.getMessage());
        }
    }

    private static Map<String, Integer> parseHeader(String headerLine) {
        String[] parts = headerLine.split(",", -1);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < parts.length; i++) {
            index.put(parts[i].trim(), i);
        }
        for (String required : REQUIRED_COLUMNS) {
            if (!index.containsKey(required)) {
                throw new TermRateCsvStructuralException("Missing required column: " + required);
            }
        }
        return index;
    }

    private static ParsedTermRateRow parseDataRow(int lineNumber, String line, Map<String, Integer> columnIndex) {
        String[] parts = line.split(",", -1);
        String tradingDateRaw = field(parts, columnIndex, "tradingDate", lineNumber);
        String institutionCode = field(parts, columnIndex, "institutionCode", lineNumber);
        String currency = field(parts, columnIndex, "currency", lineNumber);
        String tenorCode = field(parts, columnIndex, "tenor", lineNumber);
        String rateRaw = field(parts, columnIndex, "rate", lineNumber);
        LocalDate tradingDate;
        try {
            tradingDate = LocalDate.parse(tradingDateRaw);
        } catch (DateTimeParseException ex) {
            throw new TermRateCsvStructuralException(
                    "Invalid tradingDate at line " + lineNumber + ": " + tradingDateRaw);
        }
        BigDecimal rate;
        try {
            rate = new BigDecimal(rateRaw);
        } catch (NumberFormatException ex) {
            throw new TermRateCsvStructuralException("Invalid rate at line " + lineNumber + ": " + rateRaw);
        }
        return new ParsedTermRateRow(lineNumber, tradingDate, institutionCode, currency, tenorCode, rate);
    }

    private static String field(String[] parts, Map<String, Integer> columnIndex, String name, int lineNumber) {
        int idx = columnIndex.get(name);
        if (idx >= parts.length) {
            throw new TermRateCsvStructuralException("Missing " + name + " at line " + lineNumber);
        }
        return parts[idx].trim();
    }
}
