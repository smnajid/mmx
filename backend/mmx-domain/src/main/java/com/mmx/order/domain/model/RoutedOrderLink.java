package com.mmx.order.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Correlation between a client-side order and its linked hub-side order. */
public record RoutedOrderLink(
        RoutingId routingId,
        UUID clientOrderId,
        LegalEntityCode clientLegalEntityCode,
        UUID hubOrderId,
        LegalEntityCode hubLegalEntityCode) {

    public RoutedOrderLink {
        Objects.requireNonNull(routingId, "routingId must not be null");
        Objects.requireNonNull(clientOrderId, "clientOrderId must not be null");
        Objects.requireNonNull(clientLegalEntityCode, "clientLegalEntityCode must not be null");
        Objects.requireNonNull(hubOrderId, "hubOrderId must not be null");
        Objects.requireNonNull(hubLegalEntityCode, "hubLegalEntityCode must not be null");
    }
}
