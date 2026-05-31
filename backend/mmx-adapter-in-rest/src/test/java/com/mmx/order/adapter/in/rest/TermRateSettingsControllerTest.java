package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.mapper.TermRateSettingsRestMapper;
import com.mmx.order.application.port.in.ListTermRateTradingDaysUseCase;
import com.mmx.order.application.port.in.ListTermRatesForDayUseCase;
import com.mmx.order.application.port.in.UploadTermRatesUseCase;
import com.mmx.order.application.termrate.SampleTermRateCsvGenerator;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.application.termrate.TermRateCsvStructuralException;
import com.mmx.order.application.termrate.TermRateIngestFailedException;
import com.mmx.order.application.termrate.TermRateRowError;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class TermRateSettingsControllerTest {

    @Mock
    UploadTermRatesUseCase uploadTermRatesUseCase;

    @Mock
    ListTermRatesForDayUseCase listTermRatesForDayUseCase;

    @Mock
    ListTermRateTradingDaysUseCase listTermRateTradingDaysUseCase;

    @Mock
    SampleTermRateCsvGenerator sampleTermRateCsvGenerator;

    org.springframework.test.web.servlet.MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                standaloneSetup(
                                new TermRateSettingsController(
                                        uploadTermRatesUseCase,
                                        listTermRatesForDayUseCase,
                                        listTermRateTradingDaysUseCase,
                                        sampleTermRateCsvGenerator,
                                        new TermRateSettingsRestMapper()))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void uploadTermRates_returns200WithRowCount() throws Exception {
        when(uploadTermRatesUseCase.upload(any()))
                .thenReturn(
                        new UploadTermRatesUseCase.UploadResult(
                                LocalDate.of(2026, 5, 30), 2, Instant.parse("2026-05-30T10:00:00Z")));

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "rates.csv",
                        "text/csv",
                        validCsv().getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(
                        multipart("/api/v1/settings/term-rates/upload")
                                .file(file)
                                .header("X-Trader-Id", "trader-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradingDate").value("2026-05-30"))
                .andExpect(jsonPath("$.rowCount").value(2));
    }

    @Test
    void uploadTermRates_returns400ForStructuralError() throws Exception {
        when(uploadTermRatesUseCase.upload(any()))
                .thenThrow(new TermRateCsvStructuralException("Mixed trading dates"));

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "rates.csv",
                        "text/csv",
                        validCsv().getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(
                        multipart("/api/v1/settings/term-rates/upload")
                                .file(file)
                                .header("X-Trader-Id", "trader-a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TERM_RATE_STRUCTURAL_ERROR"));
    }

    @Test
    void uploadTermRates_returns400WithRowErrors() throws Exception {
        when(uploadTermRatesUseCase.upload(any()))
                .thenThrow(
                        new TermRateIngestFailedException(
                                "validation failed",
                                List.of(new TermRateRowError(2, "institutionCode", "Institution not found"))));

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "rates.csv",
                        "text/csv",
                        validCsv().getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(
                        multipart("/api/v1/settings/term-rates/upload")
                                .file(file)
                                .header("X-Trader-Id", "trader-a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].line").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("institutionCode"));
    }

    @Test
    void downloadTermRateSample_returnsCsvAttachment() throws Exception {
        when(sampleTermRateCsvGenerator.generate())
                .thenReturn("tradingDate,institutionCode,currency,tenor,rate\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/settings/term-rates/sample").header("X-Trader-Id", "trader-a"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"term-rates-sample.csv\""));
    }

    @Test
    void listTermRatesForDay_returnsRates() throws Exception {
        when(listTermRatesForDayUseCase.list(LocalDate.of(2026, 5, 30)))
                .thenReturn(
                        List.of(
                                new TermRateAuditRow(
                                        LocalDate.of(2026, 5, 30),
                                        "HSBC-01",
                                        "EUR",
                                        Tenor._1M,
                                        new BigDecimal("3.25"),
                                        Instant.parse("2026-05-30T10:00:00Z"),
                                        "trader-a")));

        mockMvc.perform(
                        get("/api/v1/settings/term-rates")
                                .header("X-Trader-Id", "trader-a")
                                .param("tradingDate", "2026-05-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].institutionCode").value("HSBC-01"))
                .andExpect(jsonPath("$[0].tenor").value("1M"));
    }

    private static String validCsv() {
        return """
                tradingDate,institutionCode,currency,tenor,rate
                2026-05-30,HSBC-01,EUR,1M,3.25000000
                2026-05-30,HSBC-01,EUR,3M,3.41000000
                """;
    }
}
