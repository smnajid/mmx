package com.mmx.order.adapter.out.messaging;

import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxRowStatus;
import com.mmx.order.adapter.out.messaging.entity.InstitutionExportOutboxEntity;
import com.mmx.order.adapter.out.messaging.repository.SpringDataInstitutionExportOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("fast")
@ExtendWith(MockitoExtension.class)
class InstitutionExportRelayWorkerTest {

    private static final String TOPIC_PREFIX = "mmx.institution";
    private static final String PAYLOAD = "{\"eventType\":\"InstitutionUpdatedV1\"}";

    @Mock
    SpringDataInstitutionExportOutboxRepository outboxRepository;

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    InstitutionExportRelayWorker worker;

    @BeforeEach
    void setUp() {
        worker = new InstitutionExportRelayWorker(outboxRepository, kafkaTemplate, TOPIC_PREFIX, 5);
    }

    private static InstitutionExportOutboxEntity pendingRow(String legalEntityCode, String institutionCode) {
        return new InstitutionExportOutboxEntity(
                UUID.randomUUID(), legalEntityCode, institutionCode, 3, PAYLOAD, BackOfficeOutboxRowStatus.PENDING, Instant.now());
    }

    private void givenPending(InstitutionExportOutboxEntity row) {
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(BackOfficeOutboxRowStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(row))
                .thenReturn(List.of());
    }

    @Test
    void publishesToTheOwningLegalEntityTopic_keyedByInstitutionCode_andMarksSentAfterAck() {
        InstitutionExportOutboxEntity row = pendingRow("PAR", "BVL-01");
        givenPending(row);
        CompletableFuture<SendResult<String, String>> acked =
                CompletableFuture.completedFuture(org.mockito.Mockito.mock(SendResult.class));
        when(kafkaTemplate.send("mmx.institution.PAR", "BVL-01", PAYLOAD)).thenReturn(acked);

        worker.drainPendingBatch(5);

        verify(kafkaTemplate).send("mmx.institution.PAR", "BVL-01", PAYLOAD);
        verify(kafkaTemplate, never()).send(eq("mmx.institution.LOC"), anyString(), anyString());
        ArgumentCaptor<InstitutionExportOutboxEntity> saved = ArgumentCaptor.forClass(InstitutionExportOutboxEntity.class);
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(BackOfficeOutboxRowStatus.SENT);
    }

    @Test
    void withoutAck_theRowStaysPendingForRetry_andCountsTheAttempt() {
        InstitutionExportOutboxEntity row = pendingRow("LOC", "HSBC-01");
        givenPending(row);
        when(kafkaTemplate.send("mmx.institution.LOC", "HSBC-01", PAYLOAD))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

        worker.drainPendingBatch(5);

        ArgumentCaptor<InstitutionExportOutboxEntity> saved = ArgumentCaptor.forClass(InstitutionExportOutboxEntity.class);
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(BackOfficeOutboxRowStatus.PENDING);
        assertThat(saved.getValue().getPublishAttempts()).isEqualTo(1);
        assertThat(saved.getValue().getLastAttemptAt()).isNotNull();
    }

    @Test
    void reachingMaxAttempts_marksTheRowTerminallyFailed() {
        InstitutionExportOutboxEntity row = pendingRow("LOC", "HSBC-01");
        row.setPublishAttempts(4);
        givenPending(row);
        when(kafkaTemplate.send("mmx.institution.LOC", "HSBC-01", PAYLOAD))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

        worker.drainPendingBatch(5);

        ArgumentCaptor<InstitutionExportOutboxEntity> saved = ArgumentCaptor.forClass(InstitutionExportOutboxEntity.class);
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(BackOfficeOutboxRowStatus.FAILED);
        assertThat(saved.getValue().getPublishAttempts()).isEqualTo(5);
    }
}
