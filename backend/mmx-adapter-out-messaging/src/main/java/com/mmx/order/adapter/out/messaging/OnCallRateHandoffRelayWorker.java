package com.mmx.order.adapter.out.messaging;

import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxRowStatus;
import com.mmx.order.adapter.out.messaging.entity.OnCallRateHandoffOutboxEntity;
import com.mmx.order.adapter.out.messaging.repository.SpringDataOnCallRateHandoffOutboxRepository;
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
        prefix = "mmx.oncall.outbox",
        name = "relay-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OnCallRateHandoffRelayWorker {

    private static final Logger log = LoggerFactory.getLogger(OnCallRateHandoffRelayWorker.class);

    private final SpringDataOnCallRateHandoffOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OnCallRateHandoffRelayWorker transactionalDelegateOrNullForTests;

    private final String topic;
    private final int maxPublishAttempts;

    @Autowired
    public OnCallRateHandoffRelayWorker(
            SpringDataOnCallRateHandoffOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${mmx.oncall.kafka.topic}") String topic,
            @Value("${mmx.oncall.outbox.max-publish-attempts:5}") int maxPublishAttempts,
            @Lazy OnCallRateHandoffRelayWorker transactionalDelegate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.maxPublishAttempts = maxPublishAttempts;
        this.transactionalDelegateOrNullForTests = transactionalDelegate;
    }

    public OnCallRateHandoffRelayWorker(
            SpringDataOnCallRateHandoffOutboxRepository outboxRepository,
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
        OnCallRateHandoffRelayWorker executor =
                transactionalDelegateOrNullForTests != null ? transactionalDelegateOrNullForTests : this;
        for (int i = 0; i < maxIterations; i++) {
            if (!executor.processOnePendingRow()) {
                break;
            }
        }
    }

    @Transactional
    public boolean processOnePendingRow() {
        List<OnCallRateHandoffOutboxEntity> rows =
                outboxRepository.findByStatusOrderByCreatedAtAsc(
                        BackOfficeOutboxRowStatus.PENDING, PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return false;
        }
        OnCallRateHandoffOutboxEntity row = rows.get(0);
        tryPublish(row);
        return true;
    }

    private void tryPublish(OnCallRateHandoffOutboxEntity row) {
        try {
            kafkaTemplate
                    .send(topic, row.getSegmentId().toString(), row.getPayload())
                    .get(30, TimeUnit.SECONDS);
            row.setStatus(BackOfficeOutboxRowStatus.SENT);
            outboxRepository.save(row);
        } catch (Exception ex) {
            handlePublishFailure(row, ex);
        }
    }

    private void handlePublishFailure(OnCallRateHandoffOutboxEntity row, Exception ex) {
        row.setPublishAttempts(row.getPublishAttempts() + 1);
        row.setLastAttemptAt(Instant.now());
        log.warn(
                "OnCall Kafka publish failed for segmentId={} attempt={}/{}: {}",
                row.getSegmentId(),
                row.getPublishAttempts(),
                maxPublishAttempts,
                ex.toString());
        if (row.getPublishAttempts() >= maxPublishAttempts) {
            row.setStatus(BackOfficeOutboxRowStatus.FAILED);
            log.warn(
                    "OnCall outbox publish exhausted; segmentId={} marked FAILED",
                    row.getSegmentId());
        }
        outboxRepository.save(row);
    }
}
