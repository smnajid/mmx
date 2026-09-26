package com.mmx.order.adapter.in.rest;

import com.mmx.order.application.port.in.ConfirmOnCallRateUseCase;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Inbound back-office OnCall rate confirmation ({@code security: []} in OpenAPI).
 */
@RestController
public class OnCallRateConfirmationCallbackController {

    private final ConfirmOnCallRateUseCase confirmOnCallRateUseCase;

    public OnCallRateConfirmationCallbackController(ConfirmOnCallRateUseCase confirmOnCallRateUseCase) {
        this.confirmOnCallRateUseCase = confirmOnCallRateUseCase;
    }

    @PostMapping(
            value = "/api/v1/back-office/oncall-rates/{segmentId}/confirmed",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> confirmOnCallRateSegment(
            @PathVariable("segmentId") UUID segmentId,
            @RequestBody(required = false) @Nullable Object body) {
        confirmOnCallRateUseCase.confirm(segmentId);
        return ResponseEntity.ok().build();
    }
}
