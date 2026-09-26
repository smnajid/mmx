package com.mmx.order.adapter.out.messaging;

import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxRowStatus;
import com.mmx.order.adapter.out.messaging.entity.InstitutionExportOutboxEntity;
import com.mmx.order.adapter.out.messaging.repository.SpringDataInstitutionExportOutboxRepository;
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

/**
 * Publishes pending {@code InstitutionUpdatedV1} rows to the owning LegalEntity's own topic,
 * {@code {prefix}.{legalEntityCode}}, keyed by {@code institutionCode}. A row is marked {@code SENT} only
 * after the broker acknowledges; failures are retried until {@code max-publish-attempts}, then {@code FAILED}.
 */
@Service
@ConditionalOnProperty(
        prefix = "mmx.institution.outbox",
        name = "relay-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InstitutionExportRelayWorker {

    private static final Logger log = LoggerFactory.getLogger(InstitutionExportRelayWorker.class);

    private final SpringDataInstitutionExportOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final InstitutionExportRelayWorker transactionalDelegateOrNullForTests;

    private final String topicPrefix;
    private final int maxPublishAttempts;

    @Autowired
    public InstitutionExportRelayWorker(
            SpringDataInstitutionExportOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${mmx.institution.kafka.topic-prefix:mmx.institution}") String topicPrefix,
            @Value("${mmx.institution.outbox.max-publish-attempts:5}") int maxPublishAttempts,
            @Lazy InstitutionExportRelayWorker transactionalDelegate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topicPrefix = topicPrefix;
        this.maxPublishAttempts = maxPublishAttempts;
        this.transactionalDelegateOrNullForTests = transactionalDelegate;
    }

    public InstitutionExportRelayWorker(
            SpringDataInstitutionExportOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            String topicPrefix,
            int maxPublishAttempts) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topicPrefix = topicPrefix;
        this.maxPublishAttempts = maxPublishAttempts;
        this.transactionalDelegateOrNullForTests = null;
    }

    public void drainPendingBatch(int maxIterations) {
        InstitutionExportRelayWorker executor =
                transactionalDelegateOrNullForTests != null ? transactionalDelegateOrNullForTests : this;
        for (int i = 0; i < maxIterations; i++) {
            if (!executor.processOnePendingRow()) {
                break;
            }
        }
    }

    @Transactional
    public boolean processOnePendingRow() {
        List<InstitutionExportOutboxEntity> rows =
                outboxRepository.findByStatusOrderByCreatedAtAsc(
                        BackOfficeOutboxRowStatus.PENDING, PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return false;
        }
        tryPublish(rows.get(0));
        return true;
    }

    String topicFor(InstitutionExportOutboxEntity row) {
        return topicPrefix + "." + row.getLegalEntityCode();
    }

    private void tryPublish(InstitutionExportOutboxEntity row) {
        try {
            kafkaTemplate
                    .send(topicFor(row), row.getInstitutionCode(), row.getPayload())
                    .get(30, TimeUnit.SECONDS);
            row.setStatus(BackOfficeOutboxRowStatus.SENT);
            outboxRepository.save(row);
        } catch (Exception ex) {
            handlePublishFailure(row, ex);
        }
    }

    private void handlePublishFailure(InstitutionExportOutboxEntity row, Exception ex) {
        row.setPublishAttempts(row.getPublishAttempts() + 1);
        row.setLastAttemptAt(Instant.now());
        log.warn(
                "Institution export publish failed for {}/{} v{} attempt={}/{}: {}",
                row.getLegalEntityCode(),
                row.getInstitutionCode(),
                row.getVersion(),
                row.getPublishAttempts(),
                maxPublishAttempts,
                ex.toString());
        if (row.getPublishAttempts() >= maxPublishAttempts) {
            row.setStatus(BackOfficeOutboxRowStatus.FAILED);
            log.warn(
                    "Institution export publish exhausted; {}/{} v{} marked FAILED",
                    row.getLegalEntityCode(),
                    row.getInstitutionCode(),
                    row.getVersion());
        }
        outboxRepository.save(row);
    }
}
