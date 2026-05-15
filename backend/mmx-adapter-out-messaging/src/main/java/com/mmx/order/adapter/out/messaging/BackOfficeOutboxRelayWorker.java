package com.mmx.order.adapter.out.messaging;

import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxEntity;
import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxRowStatus;
import com.mmx.order.adapter.out.messaging.repository.SpringDataBackOfficeOutboxRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.model.MoneyMarketOrder;

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
        prefix = "mmx.backoffice.outbox",
        name = "relay-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BackOfficeOutboxRelayWorker {

    private static final Logger log = LoggerFactory.getLogger(BackOfficeOutboxRelayWorker.class);

    private final SpringDataBackOfficeOutboxRepository outboxRepository;
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** Used from {@link #drainPendingBatch} so {@link Transactional} on {@link #processOnePendingRow} runs via proxy */
    private final BackOfficeOutboxRelayWorker transactionalDelegateOrNullForTests;

    private final String topic;
    private final int maxPublishAttempts;

    /** Spring wiring — passes lazy self-proxy so scheduler-driven batch invokes transactional methods correctly. */
    @Autowired
    public BackOfficeOutboxRelayWorker(
            SpringDataBackOfficeOutboxRepository outboxRepository,
            OrderRepository orderRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${mmx.backoffice.kafka.topic}") String topic,
            @Value("${mmx.backoffice.outbox.max-publish-attempts:5}") int maxPublishAttempts,
            @Lazy BackOfficeOutboxRelayWorker transactionalDelegate) {
        this.outboxRepository = outboxRepository;
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.maxPublishAttempts = maxPublishAttempts;
        this.transactionalDelegateOrNullForTests = transactionalDelegate;
    }

    /**
     * Manual construction (unit tests): no delegate — {@link #drainPendingBatch} calls {@code this} directly; mocks do
     * not require a transaction.
     */
    public BackOfficeOutboxRelayWorker(
            SpringDataBackOfficeOutboxRepository outboxRepository,
            OrderRepository orderRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            String topic,
            int maxPublishAttempts) {
        this.outboxRepository = outboxRepository;
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.maxPublishAttempts = maxPublishAttempts;
        this.transactionalDelegateOrNullForTests = null;
    }

    public void drainPendingBatch(int maxIterations) {
        BackOfficeOutboxRelayWorker executor =
                transactionalDelegateOrNullForTests != null ? transactionalDelegateOrNullForTests : this;
        for (int i = 0; i < maxIterations; i++) {
            if (!executor.processOnePendingRow()) {
                break;
            }
        }
    }

    /**
     * Locks and processes at most one pending outbox row.
     *
     * @return {@code true} if a row was claimed (whether publish succeeded or failed), {@code false} if queue empty
     */
    @Transactional
    public boolean processOnePendingRow() {
        List<BackOfficeOutboxEntity> rows =
                outboxRepository.findByStatusOrderByCreatedAtAsc(
                        BackOfficeOutboxRowStatus.PENDING, PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return false;
        }
        BackOfficeOutboxEntity row = rows.get(0);
        tryPublish(row);
        return true;
    }

    private void tryPublish(BackOfficeOutboxEntity row) {
        try {
            kafkaTemplate
                    .send(topic, row.getOrderId().toString(), row.getPayload())
                    .get(30, TimeUnit.SECONDS);
            row.setStatus(BackOfficeOutboxRowStatus.SENT);
            MoneyMarketOrder order =
                    orderRepository
                            .findById(row.getOrderId())
                            .orElseThrow(() -> new OrderNotFoundException(row.getOrderId()));
            order.transitionHandoffToPublished();
            orderRepository.save(order);
            outboxRepository.save(row);
        } catch (Exception ex) {
            handlePublishFailure(row, ex);
        }
    }

    private void handlePublishFailure(BackOfficeOutboxEntity row, Exception ex) {
        row.setPublishAttempts(row.getPublishAttempts() + 1);
        row.setLastAttemptAt(Instant.now());
        log.warn(
                "Kafka publish failed for orderId={} attempt={}/{}: {}",
                row.getOrderId(),
                row.getPublishAttempts(),
                maxPublishAttempts,
                ex.toString());
        if (row.getPublishAttempts() >= maxPublishAttempts) {
            row.setStatus(BackOfficeOutboxRowStatus.FAILED);
            MoneyMarketOrder order =
                    orderRepository
                            .findById(row.getOrderId())
                            .orElseThrow(() -> new OrderNotFoundException(row.getOrderId()));
            order.transitionHandoffToFailed();
            orderRepository.save(order);
            log.warn(
                    "Outbox publish exhausted; orderId={} marked FAILED (handoff integration problem)",
                    row.getOrderId());
        }
        outboxRepository.save(row);
    }
}
