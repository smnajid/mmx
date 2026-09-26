package com.mmx.order.application.service;

import com.mmx.order.application.port.in.UploadTermRatesUseCase;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.termrate.ParsedTermRateRow;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.application.termrate.TermRateCsvParseResult;
import com.mmx.order.application.termrate.TermRateCsvParser;
import com.mmx.order.application.termrate.TermRateIngestFailedException;
import com.mmx.order.application.termrate.TermRateRowError;
import com.mmx.order.domain.exception.TermRateIngestException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TermRate;
import com.mmx.order.domain.policy.TermRateIngestPolicy;

import com.mmx.order.application.port.out.Clock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class UploadTermRatesService implements UploadTermRatesUseCase {

    private final TermRateCsvParser csvParser;
    private final TermRateIngestPolicy ingestPolicy;
    private final InstitutionRepository institutionRepository;
    private final ManagedCurrencyRepository currencyRepository;
    private final TermRateRepository termRateRepository;
    private final Clock clock;

    public UploadTermRatesService(
            TermRateCsvParser csvParser,
            TermRateIngestPolicy ingestPolicy,
            InstitutionRepository institutionRepository,
            ManagedCurrencyRepository currencyRepository,
            TermRateRepository termRateRepository,
            Clock clock) {
        this.csvParser = csvParser;
        this.ingestPolicy = ingestPolicy;
        this.institutionRepository = institutionRepository;
        this.currencyRepository = currencyRepository;
        this.termRateRepository = termRateRepository;
        this.clock = clock;
    }

    @Override
    public UploadResult upload(UploadCommand command) {
        TermRateCsvParseResult parsed = csvParser.parse(command.csvBytes());
        Instant uploadedAt = clock.now();
        List<TermRateAuditRow> rows = new ArrayList<>();
        List<TermRateRowError> errors = new ArrayList<>();

        for (ParsedTermRateRow parsedRow : parsed.rows()) {
            try {
                Tenor tenor = ingestPolicy.parseTenorCode(parsedRow.line(), parsedRow.tenorCode());
                Optional<Institution> institution =
                        institutionRepository.findByInstitutionCode(parsedRow.institutionCode());
                Optional<ManagedCurrency> currency =
                        currencyRepository.findByCode(parsedRow.currency());
                TermRate termRate =
                        new TermRate(
                                parsedRow.tradingDate(),
                                parsedRow.institutionCode(),
                                parsedRow.currency(),
                                tenor,
                                parsedRow.rate());
                ingestPolicy.validateRow(parsedRow.line(), termRate, institution, currency);
                rows.add(
                        new TermRateAuditRow(
                                parsedRow.tradingDate(),
                                parsedRow.institutionCode(),
                                parsedRow.currency(),
                                tenor,
                                parsedRow.rate(),
                                uploadedAt,
                                command.uploadedBy()));
            } catch (TermRateIngestException ex) {
                errors.add(new TermRateRowError(ex.getLine(), ex.getField(), ex.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            throw new TermRateIngestFailedException("Term rate CSV validation failed", errors);
        }

        termRateRepository.replaceAllForDate(parsed.tradingDate(), rows);
        return new UploadResult(parsed.tradingDate(), rows.size(), uploadedAt);
    }
}
