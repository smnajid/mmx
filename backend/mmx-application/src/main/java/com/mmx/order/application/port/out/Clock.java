package com.mmx.order.application.port.out;

import java.time.Instant;
import java.time.LocalDate;

public interface Clock {

    Instant now();

    default LocalDate today() {
        return LocalDate.now(java.time.ZoneOffset.UTC);
    }
}
