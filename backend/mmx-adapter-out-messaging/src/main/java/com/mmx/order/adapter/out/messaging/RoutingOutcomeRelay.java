package com.mmx.order.adapter.out.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "mmx.cross-org",
        name = "role",
        havingValue = "hub")
public class RoutingOutcomeRelay {

    private final RoutingOutcomeRelayWorker worker;

    public RoutingOutcomeRelay(RoutingOutcomeRelayWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${mmx.routing-outcome.outbox.poll-interval-ms:1000}")
    public void tick() {
        worker.drainPendingBatch(20);
    }
}
