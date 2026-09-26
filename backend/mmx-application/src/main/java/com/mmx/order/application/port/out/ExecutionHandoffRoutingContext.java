package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.RoutingId;

import java.util.Optional;
import java.util.UUID;

/** Optional routing-context block for routed hub-side OrderExecutedV1 payloads. */
public record ExecutionHandoffRoutingContext(
        RoutingId routingId,
        LegalEntityCode originatingLegalEntityCode,
        UUID clientOrderId,
        String clientPortfolioNumber,
        String clientCounterparty) {

    public static ExecutionHandoffRoutingContext none() {
        return null;
    }

    public Optional<ExecutionHandoffRoutingContext> asOptional() {
        return Optional.ofNullable(this);
    }
}
