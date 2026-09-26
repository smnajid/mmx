package com.mmx.order.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.adapter.out.messaging.ExecutionHandoffOutboxAdapter;
import com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.mmx.order.application.port.out.ExecutionHandoffRoutingContext;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TraderId;
import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxEntity;
import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxRowStatus;
import com.mmx.order.adapter.out.messaging.repository.SpringDataBackOfficeOutboxRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Persistence-level transactional-outbox assertions for the execution handoff (task 3.9 of
 * fast-test-feedback-loop): the outbox row written by {@link ExecutionHandoffOutboxAdapter#schedule}
 * is asserted here on the cheap H2+Flyway stack, replacing the behavioural payload assertions that
 * used to live only in the Kafka e2e classes (they remain the relay/acceptance lock).
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import({ExecutionHandoffOutboxAdapter.class, com.mmx.order.adapter.out.messaging.OrderExecutedV1PayloadMapper.class})
@DisplayName("ExecutionHandoffOutboxAdapter transactional outbox")
class ExecutionHandoffOutboxAdapterIntegrationTest {

    @Autowired
    SpringDataOrderRepository springDataOrderRepository;

    @Autowired
    SpringDataBackOfficeOutboxRepository outboxRepository;

    @Autowired
    OrderPersistenceMapper orderMapper;

    @Autowired
    ExecutionHandoffOutboxAdapter outboxAdapter;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void cleanTables() {
        outboxRepository.deleteAll();
        springDataOrderRepository.deleteAll();
    }

    @Test
    void schedule_persists_pending_row_within_caller_transaction() throws Exception {
        MoneyMarketOrder executed = savedExecutedNativeOrder("OUTBOX-TXN-001");

        outboxAdapter.schedule(executed, ExecutionHandoffRoutingContext.none());

        BackOfficeOutboxEntity row = rowFor(executed.getId());

        assertThat(row.getStatus()).isEqualTo(BackOfficeOutboxRowStatus.PENDING);
        assertThat(row.getPublishAttempts()).isZero();

        JsonNode node = json.readTree(row.getPayload());
        assertThat(node.path("eventType").asText()).isEqualTo("OrderExecutedV1");
        assertThat(node.path("orderId").asText()).isEqualTo(executed.getId().toString());
        assertThat(node.path("externalOrderReference").asText()).isEqualTo("OUTBOX-TXN-001");
        assertThat(node.has("routingId")).isFalse();
    }

    @Test
    void schedule_with_routing_context_persists_routed_payload_fields() throws Exception {
        MoneyMarketOrder executed = savedExecutedNativeOrder("OUTBOX-ROUTED-001");
        RoutingId routingId = new RoutingId(UUID.randomUUID());
        ExecutionHandoffRoutingContext ctx =
                new ExecutionHandoffRoutingContext(
                        routingId,
                        new LegalEntityCode("PAR"),
                        UUID.randomUUID(),
                        "PAR-PM-77",
                        "BNP via LOC");

        outboxAdapter.schedule(executed, ctx);

        JsonNode node = json.readTree(rowFor(executed.getId()).getPayload());
        assertThat(node.path("routingId").asText()).isEqualTo(routingId.value().toString());
        assertThat(node.path("originatingLegalEntityCode").asText()).isEqualTo("PAR");
        assertThat(node.path("clientOrderId").asText()).isEqualTo(ctx.clientOrderId().toString());
        assertThat(node.path("clientPortfolioNumber").asText()).isEqualTo("PAR-PM-77");
        assertThat(node.path("clientCounterparty").asText()).isEqualTo("BNP via LOC");
    }

    @Test
    void schedule_without_active_transaction_is_rejected() {
        // Propagation.MANDATORY: the outbox write must never escape the caller's transaction,
        // otherwise the row could commit for a business transaction that rolled back.
        TestTransaction.end();
        try {
            assertThatThrownBy(() ->
                            outboxAdapter.schedule(savedExecutedNativeOrder("OUTBOX-MANDATORY-001"), ExecutionHandoffRoutingContext.none()))
                    .isInstanceOf(IllegalTransactionStateException.class);
        } finally {
            TestTransaction.start();
        }
    }

    private BackOfficeOutboxEntity rowFor(UUID orderId) {
        return outboxRepository.findAll().stream()
                .filter(r -> r.getOrderId().equals(orderId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outbox row for order " + orderId));
    }

    private MoneyMarketOrder savedExecutedNativeOrder(String externalRef) {
        MoneyMarketOrder order =
                MoneyMarketOrder.create(
                        new ExternalOrderReference(externalRef),
                        new LegalEntityCode("LOC"),
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        new PortfolioNumber("PF-OUTBOX-IT"),
                        "EUR",
                        new BigDecimal("5000000.00"),
                        LocalDate.of(2026, 5, 3),
                        new BigDecimal("3.25000000"),
                        Tenor._3M,
                        null,
                        null,
                        "BNKCO",
                        "BankCo",
                        LocalDate.of(2026, 5, 1));
        TraderId trader = new TraderId("trader-outbox-it");
        Instant t0 = Instant.parse("2026-05-01T10:00:00Z");
        order.assign(trader, t0);
        order.execute(
                new BigDecimal("3.5"),
                "BankCo",
                "HSBC-01",
                new DealingReference("DL-outbox-1"),
                new ContractNumber("CN-outbox-1"),
                trader,
                t0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXECUTED);
        return new JpaOrderRepository(springDataOrderRepository, orderMapper).save(order);
    }

    /** Pulls the messaging-module outbox adapter/repo/entity into the persistence test context. */
    @TestConfiguration
    @EnableJpaRepositories(
            basePackages = {
                "com.mmx.order.adapter.out.persistence.repository",
                "com.mmx.order.adapter.out.messaging.repository"
            })
    @EntityScan(
            basePackages = {
                "com.mmx.order.adapter.out.persistence.entity",
                "com.mmx.order.adapter.out.messaging.entity"
            })
    static class OutboxTestConfig {}
}
