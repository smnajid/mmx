package com.mmx.order.adapter.out.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "mmx.institution.outbox",
        name = "relay-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InstitutionExportRelay {

    private final InstitutionExportRelayWorker worker;

    public InstitutionExportRelay(InstitutionExportRelayWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${mmx.institution.outbox.poll-interval-ms:1000}")
    public void tick() {
        worker.drainPendingBatch(20);
    }
}
