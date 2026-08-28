package com.mmx.order.adapter.out.integration;

import com.mmx.order.application.port.out.Sleeper;

import java.time.Duration;

/**
 * Production {@link Sleeper} — delegates to {@link Thread#sleep(long)}. Tests use a recording fake
 * so backoff is asserted without real time elapsing.
 */
public class ThreadSleeper implements Sleeper {

    @Override
    public void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
