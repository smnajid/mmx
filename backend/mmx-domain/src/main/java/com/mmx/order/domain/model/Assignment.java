package com.mmx.order.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Assignment(TraderId traderId, Instant assignedAt) {

    public Assignment {
        Objects.requireNonNull(traderId, "traderId must not be null");
        Objects.requireNonNull(assignedAt, "assignedAt must not be null");
    }
}
