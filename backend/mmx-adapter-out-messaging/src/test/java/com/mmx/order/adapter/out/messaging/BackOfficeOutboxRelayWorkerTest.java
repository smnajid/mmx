package com.mmx.order.adapter.out.messaging;

import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxEntity;
import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxRowStatus;
import com.mmx.order.adapter.out.messaging.repository.SpringDataBackOfficeOutboxRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.MoneyMarketOrder;

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
import java.util.Optional;
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
class BackOfficeOutboxRelayWorkerTest {

    private static final String TOPIC = "mmx.order.executed";

    @Mock
    SpringDataBackOfficeOutboxRepository outboxRepository;

    @Mock
    OrderRepository orderRepository;

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    BackOfficeOutboxRelayWorker worker;

    @BeforeEach
    void setUp() {
        worker =
                new BackOfficeOutboxRelayWorker(outboxRepository, orderRepository, kafkaTemplate, TOPIC, 5);
    }

    @Test
    void publish_success_marks_sent_and_persists_order_after_transition() throws Exception {
        UUID orderId = UUID.randomUUID();
        BackOfficeOutboxEntity row =
                new BackOfficeOutboxEntity(
                        UUID.randomUUID(),
                        orderId,
                        "{\"eventType\":\"OrderExecutedV1\"}",
                        BackOfficeOutboxRowStatus.PENDING,
                        Instant.now());
        CompletableFuture<SendResult<String, String>> fut =
                CompletableFuture.completedFuture(org.mockito.Mockito.mock(SendResult.class));
        when(kafkaTemplate.send(eq(TOPIC), eq(orderId.toString()), anyString())).thenReturn(fut);

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(
                        eq(BackOfficeOutboxRowStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(row))
                .thenReturn(List.of());

        MoneyMarketOrder mo = org.mockito.Mockito.mock(MoneyMarketOrder.class);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(mo));

        worker.drainPendingBatch(5);

        verify(kafkaTemplate).send(eq(TOPIC), eq(orderId.toString()), anyString());
        ArgumentCaptor<BackOfficeOutboxEntity> boxCaptor = ArgumentCaptor.forClass(BackOfficeOutboxEntity.class);
        verify(outboxRepository).save(boxCaptor.capture());
        assertThat(boxCaptor.getValue().getStatus()).isEqualTo(BackOfficeOutboxRowStatus.SENT);

        verify(mo).transitionHandoffToPublished();
        verify(orderRepository).save(mo);
    }

    @Test
    void publish_failure_below_max_attempts_increments_attempts_and_row_stays_pending() {
        UUID orderId = UUID.randomUUID();
        BackOfficeOutboxEntity row =
                new BackOfficeOutboxEntity(
                        UUID.randomUUID(),
                        orderId,
                        "{\"eventType\":\"OrderExecutedV1\"}",
                        BackOfficeOutboxRowStatus.PENDING,
                        Instant.now());
        CompletableFuture<SendResult<String, String>> failedFut =
                CompletableFuture.failedFuture(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(eq(TOPIC), eq(orderId.toString()), anyString())).thenReturn(failedFut);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(
                        eq(BackOfficeOutboxRowStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(row))
                .thenReturn(List.of());

        worker.drainPendingBatch(5);

        ArgumentCaptor<BackOfficeOutboxEntity> boxCaptor = ArgumentCaptor.forClass(BackOfficeOutboxEntity.class);
        verify(outboxRepository).save(boxCaptor.capture());
        assertThat(boxCaptor.getValue().getStatus()).isEqualTo(BackOfficeOutboxRowStatus.PENDING);
        assertThat(boxCaptor.getValue().getPublishAttempts()).isEqualTo(1);
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void publish_failure_at_max_attempts_marks_row_failed_and_transitions_order_handoff() {
        UUID orderId = UUID.randomUUID();
        BackOfficeOutboxEntity row =
                new BackOfficeOutboxEntity(
                        UUID.randomUUID(),
                        orderId,
                        "{\"eventType\":\"OrderExecutedV1\"}",
                        BackOfficeOutboxRowStatus.PENDING,
                        Instant.now());
        row.setPublishAttempts(4); // next failure reaches maxPublishAttempts=5

        CompletableFuture<SendResult<String, String>> failedFut =
                CompletableFuture.failedFuture(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(eq(TOPIC), eq(orderId.toString()), anyString())).thenReturn(failedFut);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(
                        eq(BackOfficeOutboxRowStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(row))
                .thenReturn(List.of());

        MoneyMarketOrder mo = org.mockito.Mockito.mock(MoneyMarketOrder.class);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(mo));

        worker.drainPendingBatch(5);

        ArgumentCaptor<BackOfficeOutboxEntity> boxCaptor = ArgumentCaptor.forClass(BackOfficeOutboxEntity.class);
        verify(outboxRepository).save(boxCaptor.capture());
        assertThat(boxCaptor.getValue().getStatus()).isEqualTo(BackOfficeOutboxRowStatus.FAILED);
        assertThat(boxCaptor.getValue().getPublishAttempts()).isEqualTo(5);
        verify(mo).transitionHandoffToFailed();
        verify(orderRepository).save(mo);
    }
}
