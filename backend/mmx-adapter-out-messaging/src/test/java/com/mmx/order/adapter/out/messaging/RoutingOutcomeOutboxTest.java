package com.mmx.order.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.adapter.out.messaging.entity.RoutingOutcomeOutboxEntity;
import com.mmx.order.adapter.out.messaging.repository.SpringDataRoutingOutcomeOutboxRepository;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutedHubOrderDraft;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TraderId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Leg-B routed-order-outcome outbox adapter: each {@code schedule*} commits a row carrying only the
 * cross-boundary correlation key {@code (originatingLegalEntityCode, routingId)} — no internal order
 * UUID crosses the boundary — and each method is {@code Propagation.MANDATORY} so the row lands in
 * the caller's transaction (same-tx as the hub-side transition it mirrors).
 *
 * <p>Spec: {@code back-office-outbound-messaging} — routed-order-outcome channel; the
 * routing-failure reject path emits no row (locked at the use case, which never invokes the
 * adapter on reject).
 */
@Tag("fast")
@ExtendWith(MockitoExtension.class)
class RoutingOutcomeOutboxTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final LegalEntityCode HUB_LE = new LegalEntityCode("LOC");
    private static final LegalEntityCode CLIENT_LE = new LegalEntityCode("CGD");
    private static final Instant ACCEPTED_AT = Instant.parse("2026-05-01T12:00:00Z");

    @Mock
    SpringDataRoutingOutcomeOutboxRepository repository;

    private RoutingOutcomeOutboxAdapter adapter;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        adapter = new RoutingOutcomeOutboxAdapter(repository, Clock.fixed(ACCEPTED_AT, ZoneOffset.UTC));
    }

    @Test
    void scheduleAccepted_commits_pending_row_with_cross_boundary_key_and_no_internal_uuid()
            throws Exception {
        MoneyMarketOrder hub = hubSideOrder();

        adapter.scheduleAccepted(hub, ACCEPTED_AT);

        RoutingOutcomeOutboxEntity row = captureSaved();
        assertThat(row.getOutcomeType()).isEqualTo("ACCEPTED");
        assertThat(row.getStatus()).isEqualTo("PENDING");
        assertThat(row.getHubOrderId()).isEqualTo(hub.getId());
        assertThat(row.getOriginatingLegalEntityCode()).isEqualTo("CGD");
        assertThat(row.getRoutingId()).isEqualTo(hub.getRoutingId().value());

        JsonNode payload = json.readTree(row.getPayload());
        assertThat(payload.path("eventType").asText()).isEqualTo("RoutingOutcomeV1");
        assertThat(payload.path("outcomeType").asText()).isEqualTo("ACCEPTED");
        assertThat(payload.path("originatingLegalEntityCode").asText()).isEqualTo("CGD");
        assertThat(payload.path("routingId").asText()).isEqualTo(hub.getRoutingId().value().toString());
        assertThat(payload.path("occurredAt").asText()).isEqualTo(ACCEPTED_AT.toString());
        assertThat(payload.path("acceptedAt").asText()).isEqualTo(ACCEPTED_AT.toString());
        assertThat(payload.has("orderId")).isFalse();
        assertThat(payload.has("hubOrderId")).isFalse();
    }

    @Test
    void scheduleExecuted_commits_row_with_execution_details_and_no_internal_uuid() throws Exception {
        MoneyMarketOrder hub = executedHubOrder();

        adapter.scheduleExecuted(hub, ACCEPTED_AT);

        RoutingOutcomeOutboxEntity row = captureSaved();
        assertThat(row.getOutcomeType()).isEqualTo("EXECUTED");
        assertThat(row.getRoutingId()).isEqualTo(hub.getRoutingId().value());

        JsonNode payload = json.readTree(row.getPayload());
        assertThat(payload.path("outcomeType").asText()).isEqualTo("EXECUTED");
        assertThat(payload.path("executedRate").asText()).startsWith("3.55");
        assertThat(payload.path("institutionCode").asText()).isEqualTo("HSBC-01");
        assertThat(payload.path("dealingReference").asText()).isEqualTo("DL-001");
        assertThat(payload.path("contractNumber").asText()).isEqualTo("CN-NEW");
        assertThat(payload.has("orderId")).isFalse();
    }

    @Test
    void scheduleCancelled_commits_cancelled_row() throws Exception {
        MoneyMarketOrder hub = hubSideOrder();

        adapter.scheduleCancelled(hub, ACCEPTED_AT);

        RoutingOutcomeOutboxEntity row = captureSaved();
        assertThat(row.getOutcomeType()).isEqualTo("CANCELLED");
        JsonNode payload = json.readTree(row.getPayload());
        assertThat(payload.path("outcomeType").asText()).isEqualTo("CANCELLED");
        assertThat(payload.path("occurredAt").asText()).isEqualTo(ACCEPTED_AT.toString());
        assertThat(payload.path("cancelledAt").asText()).isEqualTo(ACCEPTED_AT.toString());
        assertThat(payload.has("orderId")).isFalse();
    }

    @Test
    void scheduleRejected_commits_rejected_row_with_reason() throws Exception {
        MoneyMarketOrder hub = hubSideOrder();

        adapter.scheduleRejected(hub, "trader declined", ACCEPTED_AT);

        RoutingOutcomeOutboxEntity row = captureSaved();
        assertThat(row.getOutcomeType()).isEqualTo("REJECTED");
        JsonNode payload = json.readTree(row.getPayload());
        assertThat(payload.path("outcomeType").asText()).isEqualTo("REJECTED");
        assertThat(payload.path("reason").asText()).isEqualTo("trader declined");
        assertThat(payload.path("rejectedAt").asText()).isEqualTo(ACCEPTED_AT.toString());
        assertThat(payload.has("orderId")).isFalse();
    }

    @Test
    void every_schedule_method_is_transactional_mandatory_for_same_tx_semantics() throws Exception {
        for (String name :
                new String[] {"scheduleAccepted", "scheduleExecuted", "scheduleCancelled", "scheduleRejected"}) {
            Method method = RoutingOutcomeOutboxAdapter.class.getMethod(
                    name,
                    name.equals("scheduleRejected")
                            ? new Class[] {MoneyMarketOrder.class, String.class, Instant.class}
                            : new Class[] {MoneyMarketOrder.class, Instant.class});
            Transactional tx = method.getAnnotation(Transactional.class);
            assertThat(tx).as("%s must be @Transactional", name).isNotNull();
            assertThat(tx.propagation())
                    .as("%s must be Propagation.MANDATORY", name)
                    .isEqualTo(Propagation.MANDATORY);
        }
    }

    @Test
    void scheduleAccepted_uses_injected_clock_for_createdAt_not_wall_clock() {
        MoneyMarketOrder hub = hubSideOrder();

        adapter.scheduleAccepted(hub, ACCEPTED_AT);

        RoutingOutcomeOutboxEntity row = captureSaved();
        assertThat(row.getCreatedAt()).isEqualTo(ACCEPTED_AT);
    }

    private RoutingOutcomeOutboxEntity captureSaved() {
        ArgumentCaptor<RoutingOutcomeOutboxEntity> captor =
                ArgumentCaptor.forClass(RoutingOutcomeOutboxEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static MoneyMarketOrder hubSideOrder() {
        return MoneyMarketOrder.createHubSideFromRouting(draft(), TODAY);
    }

    private static MoneyMarketOrder executedHubOrder() {
        MoneyMarketOrder hub = hubSideOrder();
        hub.assign(new TraderId("alice"), ACCEPTED_AT);
        hub.execute(
                new BigDecimal("3.55"),
                "BankCo International",
                "HSBC-01",
                new DealingReference("DL-001"),
                new ContractNumber("CN-NEW"),
                new TraderId("alice"),
                ACCEPTED_AT);
        return hub;
    }

    private static RoutedHubOrderDraft draft() {
        return new RoutedHubOrderDraft(
                HUB_LE,
                new PortfolioNumber("LOC-EUR-001"),
                "HSBC-01",
                "BankCo International",
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                Tenor._3M,
                null,
                new BigDecimal("3.25"),
                null,
                RoutingId.fromClientOrderId(UUID.randomUUID()),
                CLIENT_LE,
                new ExternalOrderReference("CGD-PM-1"));
    }
}
