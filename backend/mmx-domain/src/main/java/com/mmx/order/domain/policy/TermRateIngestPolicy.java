package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.TermRateIngestException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TermRate;

import java.math.BigDecimal;
import java.util.Optional;

public final class TermRateIngestPolicy {

    public void validateRow(
            int line,
            TermRate row,
            Optional<Institution> institutionOpt,
            Optional<ManagedCurrency> currencyOpt) {
        validateInstitution(line, row.getInstitutionCode(), institutionOpt);
        validateCurrency(line, row.getCurrency(), currencyOpt);
        validateTenorEnabled(line, row.getTenor(), currencyOpt);
        validateRate(line, row.getRate());
    }

    public Tenor parseTenorCode(int line, String tenorCode) {
        return Tenor.fromCode(tenorCode)
                .orElseThrow(
                        () -> new TermRateIngestException(line, "tenor", "Invalid tenor code: " + tenorCode));
    }

    private static void validateInstitution(int line, String institutionCode, Optional<Institution> institutionOpt) {
        if (institutionOpt.isEmpty()) {
            throw new TermRateIngestException(
                    line, "institutionCode", "Institution not found: " + institutionCode);
        }
        if (!institutionOpt.get().isActive()) {
            throw new TermRateIngestException(
                    line, "institutionCode", "Institution is not active: " + institutionCode);
        }
    }

    private static void validateCurrency(int line, String currencyCode, Optional<ManagedCurrency> currencyOpt) {
        if (currencyOpt.isEmpty()) {
            throw new TermRateIngestException(line, "currency", "Currency is not managed: " + currencyCode);
        }
        ManagedCurrency currency = currencyOpt.get();
        if (!currency.isActive()) {
            throw new TermRateIngestException(line, "currency", "Currency is not active: " + currencyCode);
        }
        if (!currency.getCode().equals(currencyCode)) {
            throw new TermRateIngestException(line, "currency", "Currency mismatch for managed configuration");
        }
    }

    private static void validateTenorEnabled(int line, Tenor tenor, Optional<ManagedCurrency> currencyOpt) {
        if (currencyOpt.isEmpty()) {
            return;
        }
        if (!currencyOpt.get().getEnabledTenors().contains(tenor)) {
            throw new TermRateIngestException(
                    line,
                    "tenor",
                    "Tenor " + tenor.getCode() + " is not enabled for currency " + currencyOpt.get().getCode());
        }
    }

    private static void validateRate(int line, BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TermRateIngestException(line, "rate", "Rate must be greater than zero");
        }
        if (rate.scale() > 8) {
            throw new TermRateIngestException(line, "rate", "Rate must have at most 8 fractional digits");
        }
    }
}
