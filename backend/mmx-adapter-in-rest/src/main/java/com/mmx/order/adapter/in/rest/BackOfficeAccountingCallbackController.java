package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.generated.api.BackOfficeApi;
import com.mmx.order.adapter.in.rest.generated.model.MarkOrderAccountedRequest;
import com.mmx.order.application.port.in.MarkOrderAccountedUseCase;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.annotation.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Implements {@link BackOfficeApi}. Accounting callback is deliberately unauthenticated in this baseline
 * ({@code security: []} in OpenAPI).
 */
@RestController
public class BackOfficeAccountingCallbackController implements BackOfficeApi {

    private final MarkOrderAccountedUseCase markOrderAccountedUseCase;

    public BackOfficeAccountingCallbackController(MarkOrderAccountedUseCase markOrderAccountedUseCase) {
        this.markOrderAccountedUseCase = markOrderAccountedUseCase;
    }

    @Override
    @PostMapping(
            value = "/api/v1/back-office/orders/{orderId}/accounted",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> markOrderAccounted(
            @PathVariable("orderId") UUID orderId,
            @RequestBody(required = false) @Nullable MarkOrderAccountedRequest markOrderAccountedRequest) {
        markOrderAccountedUseCase.markAccounted(orderId);
        return ResponseEntity.ok().build();
    }
}
