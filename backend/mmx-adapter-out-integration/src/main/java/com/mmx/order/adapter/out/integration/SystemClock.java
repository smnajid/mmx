package com.mmx.order.adapter.out.integration;

import com.mmx.order.application.port.out.Clock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class SystemClock implements Clock {

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
