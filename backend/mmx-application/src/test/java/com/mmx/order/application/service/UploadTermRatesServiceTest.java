package com.mmx.order.application.service;

import com.mmx.order.application.port.in.UploadTermRatesUseCase;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.application.termrate.TermRateCsvParser;
import com.mmx.order.application.termrate.TermRateIngestFailedException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.policy.TermRateIngestPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import com.mmx.order.application.port.out.Clock;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadTermRatesServiceTest {

    private static final Clock CLOCK = () -> Instant.parse("2026-05-30T10:00:00Z");

    @Mock
    InstitutionRepository institutionRepository;

    @Mock
    ManagedCurrencyRepository currencyRepository;

    @Mock
    TermRateRepository termRateRepository;

    UploadTermRatesService subject;

    private final Institution hsbc = new Institution("HSBC-01", "HSBC", true);
    private final ManagedCurrency eur =
            new ManagedCurrency(
                    "EUR",
                    true,
                    new BigDecimal("1000"),
                    new BigDecimal("500"),
                    EnumSet.of(Tenor._1M, Tenor._3M),
                    EnumSet.noneOf(com.mmx.order.domain.model.NoticePeriod.class));

    @BeforeEach
    void setUp() {
        subject =
                new UploadTermRatesService(
                        new TermRateCsvParser(),
                        new TermRateIngestPolicy(),
                        institutionRepository,
                        currencyRepository,
                        termRateRepository,
                        CLOCK);
    }

    @Test
    void upload_persistsRowsOnSuccess() {
        when(institutionRepository.findByInstitutionCode("HSBC-01")).thenReturn(Optional.of(hsbc));
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

        byte[] csv = validCsv();
        UploadTermRatesUseCase.UploadResult result =
                subject.upload(new UploadTermRatesUseCase.UploadCommand(csv, "trader-1"));

        assertThat(result.tradingDate()).isEqualTo(LocalDate.of(2026, 5, 30));
        assertThat(result.rowCount()).isEqualTo(1);

        ArgumentCaptor<List<TermRateAuditRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(termRateRepository).replaceAllForDate(eq(LocalDate.of(2026, 5, 30)), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().uploadedBy()).isEqualTo("trader-1");
    }

    @Test
    void upload_replaceDayOverwrites() {
        when(institutionRepository.findByInstitutionCode("HSBC-01")).thenReturn(Optional.of(hsbc));
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

        subject.upload(new UploadTermRatesUseCase.UploadCommand(validCsv(), "trader-1"));
        subject.upload(new UploadTermRatesUseCase.UploadCommand(validCsv(), "trader-2"));

        verify(termRateRepository, times(2)).replaceAllForDate(eq(LocalDate.of(2026, 5, 30)), any());
    }

    @Test
    void upload_rowErrorDoesNotPersist() {
        when(institutionRepository.findByInstitutionCode("NOPE-01")).thenReturn(Optional.empty());
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

        assertThatThrownBy(
                        () ->
                                subject.upload(
                                        new UploadTermRatesUseCase.UploadCommand(
                                                """
                                                tradingDate,institutionCode,currency,tenor,rate
                                                2026-05-30,NOPE-01,EUR,1M,3.25000000
                                                """
                                                        .getBytes(StandardCharsets.UTF_8),
                                                "trader-1")))
                .isInstanceOf(TermRateIngestFailedException.class);

        verify(termRateRepository, never()).replaceAllForDate(any(), any());
    }

    private static byte[] validCsv() {
        return """
                tradingDate,institutionCode,currency,tenor,rate
                2026-05-30,HSBC-01,EUR,1M,3.25000000
                """
                .getBytes(StandardCharsets.UTF_8);
    }
}
