package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.generated.termrate.api.TermRateSettingsApi;
import com.mmx.order.adapter.in.rest.generated.termrate.model.TermRateResponse;
import com.mmx.order.adapter.in.rest.generated.termrate.model.TermRateTradingDayResponse;
import com.mmx.order.adapter.in.rest.generated.termrate.model.TermRateUploadResponse;
import com.mmx.order.adapter.in.rest.mapper.TermRateSettingsRestMapper;
import com.mmx.order.application.port.in.ListTermRateTradingDaysUseCase;
import com.mmx.order.application.port.in.ListTermRatesForDayUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.in.UploadTermRatesUseCase;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.application.port.out.ReferenceDataMutationGuard;
import com.mmx.order.application.termrate.SampleTermRateCsvGenerator;
import com.mmx.order.application.termrate.TermRateCsvStructuralException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
public class TermRateSettingsController implements TermRateSettingsApi {

    private final UploadTermRatesUseCase uploadTermRatesUseCase;
    private final ListTermRatesForDayUseCase listTermRatesForDayUseCase;
    private final ListTermRateTradingDaysUseCase listTermRateTradingDaysUseCase;
    private final SampleTermRateCsvGenerator sampleTermRateCsvGenerator;
    private final TermRateSettingsRestMapper mapper;
    private final ScopeContextProvider scopeContextProvider;
    private final ReferenceDataMutationGuard mutationGuard;

    public TermRateSettingsController(
            UploadTermRatesUseCase uploadTermRatesUseCase,
            ListTermRatesForDayUseCase listTermRatesForDayUseCase,
            ListTermRateTradingDaysUseCase listTermRateTradingDaysUseCase,
            SampleTermRateCsvGenerator sampleTermRateCsvGenerator,
            TermRateSettingsRestMapper mapper,
            ScopeContextProvider scopeContextProvider,
            ReferenceDataMutationGuard mutationGuard) {
        this.uploadTermRatesUseCase = uploadTermRatesUseCase;
        this.listTermRatesForDayUseCase = listTermRatesForDayUseCase;
        this.listTermRateTradingDaysUseCase = listTermRateTradingDaysUseCase;
        this.sampleTermRateCsvGenerator = sampleTermRateCsvGenerator;
        this.mapper = mapper;
        this.scopeContextProvider = scopeContextProvider;
        this.mutationGuard = mutationGuard;
    }

    @Override
    public ResponseEntity<TermRateUploadResponse> uploadTermRates(String xTraderId, MultipartFile file) {
        ScopeContext scope = scopeContextProvider.requireActiveScope();
        mutationGuard.ensureTrader(scope);
        try {
            UploadTermRatesUseCase.UploadResult result =
                    uploadTermRatesUseCase.upload(
                            new UploadTermRatesUseCase.UploadCommand(file.getBytes(), xTraderId));
            return ResponseEntity.ok(mapper.toUploadResponse(result));
        } catch (IOException ex) {
            throw new TermRateCsvStructuralException("Failed to read uploaded file: " + ex.getMessage());
        }
    }

    @Override
    public ResponseEntity<List<TermRateResponse>> listTermRatesForDay(String xTraderId, LocalDate tradingDate) {
        List<TermRateResponse> body =
                listTermRatesForDayUseCase.list(tradingDate).stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<List<TermRateTradingDayResponse>> listTermRateTradingDays(String xTraderId) {
        List<TermRateTradingDayResponse> body =
                listTermRateTradingDaysUseCase.list().stream().map(mapper::toTradingDayResponse).toList();
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Resource> downloadTermRateSample(String xTraderId) {
        byte[] csv = sampleTermRateCsvGenerator.generate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"term-rates-sample.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(new ByteArrayResource(csv));
    }
}
