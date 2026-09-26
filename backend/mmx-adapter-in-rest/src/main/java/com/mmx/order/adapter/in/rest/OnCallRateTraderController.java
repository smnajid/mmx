package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.generated.api.OnCallRateSettingsApi;
import com.mmx.order.adapter.in.rest.generated.model.AddOnCallRateRequest;
import com.mmx.order.adapter.in.rest.generated.model.OnCallRateSegmentResponse;
import com.mmx.order.adapter.in.rest.mapper.OnCallRateRestMapper;
import com.mmx.order.application.command.CancelOnCallRateCommand;
import com.mmx.order.application.port.in.AddOnCallRateUseCase;
import com.mmx.order.application.port.in.CancelOnCallRateUseCase;
import com.mmx.order.application.port.in.ListOnCallRateSegmentsUseCase;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.application.port.out.ReferenceDataMutationGuard;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class OnCallRateTraderController implements OnCallRateSettingsApi {

    private final AddOnCallRateUseCase addOnCallRateUseCase;
    private final CancelOnCallRateUseCase cancelOnCallRateUseCase;
    private final ListOnCallRateSegmentsUseCase listOnCallRateSegmentsUseCase;
    private final OnCallRateRestMapper mapper;
    private final ScopeContextProvider scopeContextProvider;
    private final ReferenceDataMutationGuard mutationGuard;

    public OnCallRateTraderController(
            AddOnCallRateUseCase addOnCallRateUseCase,
            CancelOnCallRateUseCase cancelOnCallRateUseCase,
            ListOnCallRateSegmentsUseCase listOnCallRateSegmentsUseCase,
            OnCallRateRestMapper mapper,
            ScopeContextProvider scopeContextProvider,
            ReferenceDataMutationGuard mutationGuard) {
        this.addOnCallRateUseCase = addOnCallRateUseCase;
        this.cancelOnCallRateUseCase = cancelOnCallRateUseCase;
        this.listOnCallRateSegmentsUseCase = listOnCallRateSegmentsUseCase;
        this.mapper = mapper;
        this.scopeContextProvider = scopeContextProvider;
        this.mutationGuard = mutationGuard;
    }

    @Override
    public ResponseEntity<OnCallRateSegmentResponse> addOnCallRate(
            String xTraderId, String institutionCode, AddOnCallRateRequest addOnCallRateRequest) {
        mutationGuard.ensureTrader(scopeContextProvider.requireActiveScope());
        var segment =
                addOnCallRateUseCase.add(mapper.toAddCommand(institutionCode, addOnCallRateRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(segment));
    }

    @Override
    public ResponseEntity<OnCallRateSegmentResponse> cancelOnCallRate(
            String xTraderId, String institutionCode, UUID segmentId) {
        mutationGuard.ensureTrader(scopeContextProvider.requireActiveScope());
        var segment =
                cancelOnCallRateUseCase.cancel(new CancelOnCallRateCommand(institutionCode, segmentId));
        return ResponseEntity.ok(mapper.toResponse(segment));
    }

    @Override
    public ResponseEntity<List<OnCallRateSegmentResponse>> listOnCallRateSegments(
            String xTraderId, String institutionCode) {
        List<OnCallRateSegmentResponse> body =
                listOnCallRateSegmentsUseCase.listByInstitution(institutionCode).stream()
                        .map(mapper::toResponse)
                        .toList();
        return ResponseEntity.ok(body);
    }
}
