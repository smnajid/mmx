package com.mmx.order.adapter.out.messaging;

import com.mmx.order.application.port.in.ApplyRemoteOrderOutcomeUseCase;
import com.mmx.order.application.port.in.RemoteOrderOutcome;
import com.mmx.order.domain.model.ExecutionDetails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CGED leg-B Kafka consumer adapter: decodes the canonical {@code RoutingOutcomeV1} payload,
 * filters by {@code originatingLegalEntityCode ∈ {this deployment's own LEs}}, and delegates to
 * {@link ApplyRemoteOrderOutcomeUseCase}. Non-matching events are skipped (the producer is blind to
 * who is listening; the client filters on consume).
 *
 * <p>Spec: {@code back-office-outbound-messaging} — routed-order-outcome channel; spec
 * {@code order-routing} — silence is never terminal.
 */
@Tag("fast")
@ExtendWith(MockitoExtension.class)
class RoutingOutcomeConsumerTest {

    private static final String OWN_LE = "CGD";
    private static final String ROUTING_ID = "11111111-2222-3333-4444-555555555555";
    private static final Instant OCCURRED_AT = Instant.parse("2026-05-01T12:00:00Z");

    @Mock
    ApplyRemoteOrderOutcomeUseCase useCase;

    private RoutingOutcomeV1Consumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RoutingOutcomeV1Consumer(useCase, Set.of(OWN_LE));
    }

    @Test
    void accepted_for_own_le_is_decoded_and_applied() {
        consumer.onRoutingOutcome(json("ACCEPTED", OWN_LE, """
                "acceptedAt": "%s"
                """.formatted(OCCURRED_AT)));

        RemoteOrderOutcome outcome = captureApplied();
        assertThat(outcome).isInstanceOf(RemoteOrderOutcome.Accepted.class);
        assertThat(outcome.originatingLegalEntityCode().value()).isEqualTo(OWN_LE);
        assertThat(outcome.routingId().value().toString()).isEqualTo(ROUTING_ID);
        assertThat(((RemoteOrderOutcome.Accepted) outcome).acceptedAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void executed_for_own_le_carries_hub_execution_details() {
        consumer.onRoutingOutcome(json("EXECUTED", OWN_LE, """
                "executedAt": "%s",
                "executedRate": 3.55,
                "institutionCode": "HSBC-01",
                "dealingReference": "DL-001",
                "contractNumber": "CN-NEW"
                """.formatted(OCCURRED_AT)));

        RemoteOrderOutcome outcome = captureApplied();
        assertThat(outcome).isInstanceOf(RemoteOrderOutcome.Executed.class);
        ExecutionDetails exec = ((RemoteOrderOutcome.Executed) outcome).hubExecution();
        assertThat(exec.executedRate()).isEqualByComparingTo(new BigDecimal("3.55"));
        assertThat(exec.institutionCode()).isEqualTo("HSBC-01");
        assertThat(exec.dealingReference().value()).isEqualTo("DL-001");
        assertThat(exec.generatedContractNumber().value()).isEqualTo("CN-NEW");
        assertThat(((RemoteOrderOutcome.Executed) outcome).executedAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void cancelled_for_own_le_is_applied() {
        consumer.onRoutingOutcome(json("CANCELLED", OWN_LE, """
                "cancelledAt": "%s"
                """.formatted(OCCURRED_AT)));

        RemoteOrderOutcome outcome = captureApplied();
        assertThat(outcome).isInstanceOf(RemoteOrderOutcome.Cancelled.class);
        assertThat(((RemoteOrderOutcome.Cancelled) outcome).cancelledAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void rejected_for_own_le_carries_reason() {
        consumer.onRoutingOutcome(json("REJECTED", OWN_LE, """
                "rejectedAt": "%s",
                "reason": "trader declined"
                """.formatted(OCCURRED_AT)));

        RemoteOrderOutcome outcome = captureApplied();
        assertThat(outcome).isInstanceOf(RemoteOrderOutcome.Rejected.class);
        assertThat(((RemoteOrderOutcome.Rejected) outcome).reason()).isEqualTo("trader declined");
    }

    @Test
    void outcome_for_other_originating_le_is_skipped() {
        consumer.onRoutingOutcome(json("ACCEPTED", "PAR", """
                "acceptedAt": "%s"
                """.formatted(OCCURRED_AT)));

        verify(useCase, never()).apply(org.mockito.ArgumentMatchers.any());
    }

    private RemoteOrderOutcome captureApplied() {
        ArgumentCaptor<RemoteOrderOutcome> captor = ArgumentCaptor.forClass(RemoteOrderOutcome.class);
        verify(useCase).apply(captor.capture());
        return captor.getValue();
    }

    private static String json(String outcomeType, String originatingLe, String variantFields) {
        return """
                {
                  "eventType": "RoutingOutcomeV1",
                  "outcomeType": "%s",
                  "originatingLegalEntityCode": "%s",
                  "routingId": "%s",
                  "occurredAt": "%s",
                  %s
                }
                """.formatted(outcomeType, originatingLe, ROUTING_ID, OCCURRED_AT, variantFields);
    }
}
