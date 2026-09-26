package com.mmx.order.adapter.out.messaging;

import com.mmx.order.adapter.out.messaging.entity.RoutingOutcomeOutboxEntity;
import com.mmx.order.adapter.out.messaging.repository.SpringDataRoutingOutcomeOutboxRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(
        prefix = "mmx.cross-org",
        name = "role",
        havingValue = "hub")
public class RoutingOutcomeRelayWorker {

    private static final Logger log = LoggerFactory.getLogger(RoutingOutcomeRelayWorker.class);

    static final String PENDING = "PENDING";
    static final String PUBLISHED = "PUBLISHED";
    static final String FAILED = "FAILED";

    private final SpringDataRoutingOutcomeOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RoutingOutcomeRelayWorker transactionalDelegateOrNullForTests;

    private final String topic;
    private final int maxPublishAttempts;

    @Autowired
    public RoutingOutcomeRelayWorker(
            SpringDataRoutingOutcomeOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${mmx.cross-org.outcome-topic:mmx.routed-order-outcome.unconfigured}") String topic,
            @Value("${mmx.routing-outcome.outbox.max-publish-attempts:5}") int maxPublishAttempts,
            @Lazy RoutingOutcomeRelayWorker transactionalDelegate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.maxPublishAttempts = maxPublishAttempts;
        this.transactionalDelegateOrNullForTests = transactionalDelegate;
    }

    public RoutingOutcomeRelayWorker(
            SpringDataRoutingOutcomeOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            String topic,
            int maxPublishAttempts) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.maxPublishAttempts = maxPublishAttempts;
        this.transactionalDelegateOrNullForTests = null;
    }

    public void drainPendingBatch(int maxIterations) {
        RoutingOutcomeRelayWorker executor =
                transactionalDelegateOrNullForTests != null ? transactionalDelegateOrNullForTests : this;
        for (int i = 0; i < maxIterations; i++) {
            if (!executor.processOnePendingRow()) {
                break;
            }
        }
    }

    @Transactional
    public boolean processOnePendingRow() {
        List<RoutingOutcomeOutboxEntity> rows =
                outboxRepository.findByStatusOrderByCreatedAtAsc(PENDING, PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return false;
        }
        tryPublish(rows.get(0));
        return true;
    }

    private void tryPublish(RoutingOutcomeOutboxEntity row) {
        String key = row.getOriginatingLegalEntityCode();
        try {
            kafkaTemplate
                    .send(topic, key, row.getPayload())
                    .get(30, TimeUnit.SECONDS);
            row.setStatus(PUBLISHED);
            outboxRepository.save(row);
        } catch (Exception ex) {
            handlePublishFailure(row, ex);
        }
    }

    private void handlePublishFailure(RoutingOutcomeOutboxEntity row, Exception ex) {
        row.incrementPublishAttempts();
        row.setLastAttemptAt(Instant.now());
        log.warn(
                "Routing-outcome Kafka publish failed for originatingLe={} routingId={} attempt={}/{}: {}",
                row.getOriginatingLegalEntityCode(),
                row.getRoutingId(),
                row.getPublishAttempts(),
                maxPublishAttempts,
                ex.toString());
        if (row.getPublishAttempts() >= maxPublishAttempts) {
            row.setStatus(FAILED);
            log.warn(
                    "Routing-outcome outbox publish exhausted; originatingLe={} routingId={} marked FAILED",
                    row.getOriginatingLegalEntityCode(),
                    row.getRoutingId());
        }
        outboxRepository.save(row);
    }
}
