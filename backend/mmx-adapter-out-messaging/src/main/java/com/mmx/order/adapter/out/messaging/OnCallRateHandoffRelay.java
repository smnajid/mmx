package com.mmx.order.adapter.out.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "mmx.oncall.outbox",
        name = "relay-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OnCallRateHandoffRelay {

    private final OnCallRateHandoffRelayWorker worker;

    public OnCallRateHandoffRelay(OnCallRateHandoffRelayWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${mmx.oncall.outbox.poll-interval-ms:1000}")
    public void tick() {
        worker.drainPendingBatch(20);
    }
}
