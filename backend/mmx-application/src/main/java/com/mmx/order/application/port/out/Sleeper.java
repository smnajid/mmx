package com.mmx.order.application.port.out;

import java.time.Duration;

/**
 * Testable backoff sleep. Production implementation delegates to {@link Thread#sleep(long)}; tests use
 * a recording fake so backoff is asserted without real time elapsing. Owned by the retry/circuit
 * policy — no domain state for retry (retry/backoff/circuit are infrastructure-owned metadata).
 */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration);
}
