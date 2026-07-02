package com.mmx.order.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Deterministic routing correlation id derived from the client-side order id. */
public record RoutingId(UUID value) {

    public RoutingId {
        Objects.requireNonNull(value, "routing id value must not be null");
    }

    public static RoutingId fromClientOrderId(UUID clientOrderId) {
        Objects.requireNonNull(clientOrderId, "clientOrderId must not be null");
        return new RoutingId(
                UUID.nameUUIDFromBytes(("mmx-routing:" + clientOrderId).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
