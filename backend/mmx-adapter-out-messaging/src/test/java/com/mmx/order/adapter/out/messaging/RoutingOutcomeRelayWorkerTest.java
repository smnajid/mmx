package com.mmx.order.adapter.out.messaging;

import com.mmx.order.adapter.out.messaging.entity.RoutingOutcomeOutboxEntity;
import com.mmx.order.adapter.out.messaging.repository.SpringDataRoutingOutcomeOutboxRepository;

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
class RoutingOutcomeRelayWorkerTest {

    private static final String TOPIC = "mmx.routed-order-outcome.LODH";
    private static final String PENDING = "PENDING";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String FAILED = "FAILED";

    @Mock
    SpringDataRoutingOutcomeOutboxRepository outboxRepository;

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    RoutingOutcomeRelayWorker worker;

    @BeforeEach
    void setUp() {
        worker = new RoutingOutcomeRelayWorker(outboxRepository, kafkaTemplate, TOPIC, 5);
    }

    @Test
    void publish_success_publishes_keyed_by_originating_le_and_marks_row_published() throws Exception {
        UUID routingId = UUID.randomUUID();
        String originatingLe = "CGD";
        RoutingOutcomeOutboxEntity row = pendingRow(routingId, originatingLe, "ACCEPTED");

        CompletableFuture<SendResult<String, String>> fut =
                CompletableFuture.completedFuture(org.mockito.Mockito.mock(SendResult.class));
        when(kafkaTemplate.send(eq(TOPIC), eq(originatingLe), anyString())).thenReturn(fut);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(PENDING), any(Pageable.class)))
                .thenReturn(List.of(row))
                .thenReturn(List.of());

        worker.drainPendingBatch(5);

        verify(kafkaTemplate).send(eq(TOPIC), eq(originatingLe), anyString());
        ArgumentCaptor<RoutingOutcomeOutboxEntity> captor =
                ArgumentCaptor.forClass(RoutingOutcomeOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PUBLISHED);
    }

    @Test
    void empty_queue_publishes_nothing() {
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(PENDING), any(Pageable.class)))
                .thenReturn(List.of());

        worker.drainPendingBatch(5);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void publish_failure_below_max_attempts_increments_attempts_and_row_stays_pending() {
        UUID routingId = UUID.randomUUID();
        RoutingOutcomeOutboxEntity row = pendingRow(routingId, "CGD", "EXECUTED");

        CompletableFuture<SendResult<String, String>> failedFut =
                CompletableFuture.failedFuture(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(eq(TOPIC), eq("CGD"), anyString())).thenReturn(failedFut);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(PENDING), any(Pageable.class)))
                .thenReturn(List.of(row))
                .thenReturn(List.of());

        worker.drainPendingBatch(5);

        ArgumentCaptor<RoutingOutcomeOutboxEntity> captor =
                ArgumentCaptor.forClass(RoutingOutcomeOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PENDING);
        assertThat(captor.getValue().getPublishAttempts()).isEqualTo(1);
    }

    @Test
    void publish_failure_at_max_attempts_marks_row_failed() {
        UUID routingId = UUID.randomUUID();
        RoutingOutcomeOutboxEntity row = pendingRow(routingId, "CGD", "CANCELLED");
        row.incrementPublishAttempts();
        row.incrementPublishAttempts();
        row.incrementPublishAttempts();
        row.incrementPublishAttempts();

        CompletableFuture<SendResult<String, String>> failedFut =
                CompletableFuture.failedFuture(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(eq(TOPIC), eq("CGD"), anyString())).thenReturn(failedFut);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(PENDING), any(Pageable.class)))
                .thenReturn(List.of(row))
                .thenReturn(List.of());

        worker.drainPendingBatch(5);

        ArgumentCaptor<RoutingOutcomeOutboxEntity> captor =
                ArgumentCaptor.forClass(RoutingOutcomeOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FAILED);
        assertThat(captor.getValue().getPublishAttempts()).isEqualTo(5);
    }

    private static RoutingOutcomeOutboxEntity pendingRow(UUID routingId, String originatingLe, String outcomeType) {
        return new RoutingOutcomeOutboxEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                originatingLe,
                routingId,
                outcomeType,
                "{\"eventType\":\"RoutingOutcomeV1\",\"outcomeType\":\"" + outcomeType + "\"}",
                PENDING,
                Instant.now());
    }
}
