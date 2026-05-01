package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MoneyMarketOrder lifecycle")
class MoneyMarketOrderLifecycleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final Instant NOW = Instant.parse("2026-05-01T10:00:00Z");
    private static final TraderId TRADER_A = new TraderId("trader-a");
    private static final TraderId TRADER_B = new TraderId("trader-b");

    private MoneyMarketOrder receivedOrder;

    @BeforeEach
    void setUp() {
        receivedOrder = MoneyMarketOrder.create(
                new ExternalOrderReference("PM-LIFECYCLE-001"),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-001"),
                "EUR",
                new BigDecimal("5000000.00"),
                TODAY.plusDays(2),
                new BigDecimal("3.25000000"),
                Tenor._3M,
                null, null, null,
                TODAY
        );
    }

    // ── Assign ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("assign")
    class Assign {

        @Test
        void assign_from_received_sets_status_and_assignment() {
            receivedOrder.assign(TRADER_A, NOW);

            assertThat(receivedOrder.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(receivedOrder.getAssignment()).isNotNull();
            assertThat(receivedOrder.getAssignment().traderId()).isEqualTo(TRADER_A);
            assertThat(receivedOrder.getAssignment().assignedAt()).isEqualTo(NOW);
        }

        @Test
        void assign_from_executed_throws() {
            receivedOrder.assign(TRADER_A, NOW);
            receivedOrder.execute(
                    new BigDecimal("3.50000000"), "BankCo",
                    new DealingReference("DL-abc"), new ContractNumber("CN-abc"),
                    TRADER_A, NOW
            );

            assertThatThrownBy(() -> receivedOrder.assign(TRADER_B, NOW))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    // ── Unassign ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("unassign")
    class Unassign {

        @BeforeEach
        void assign() {
            receivedOrder.assign(TRADER_A, NOW);
        }

        @Test
        void unassign_by_assigned_trader_returns_to_received() {
            receivedOrder.unassign(TRADER_A, NOW);

            assertThat(receivedOrder.getStatus()).isEqualTo(OrderStatus.RECEIVED);
            assertThat(receivedOrder.getAssignment()).isNull();
        }

        @Test
        void unassign_by_different_trader_throws() {
            assertThatThrownBy(() -> receivedOrder.unassign(TRADER_B, NOW))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    // ── Execute ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("execute")
    class Execute {

        @BeforeEach
        void assign() {
            receivedOrder.assign(TRADER_A, NOW);
        }

        @Test
        void execute_by_assigned_trader_sets_execution_details() {
            DealingReference dealRef = new DealingReference("DL-exec-001");
            ContractNumber contractNum = new ContractNumber("CN-exec-001");

            receivedOrder.execute(
                    new BigDecimal("3.50000000"), "BankCo International",
                    dealRef, contractNum, TRADER_A, NOW
            );

            assertThat(receivedOrder.getStatus()).isEqualTo(OrderStatus.EXECUTED);
            assertThat(receivedOrder.getExecutionDetails()).isNotNull();
            assertThat(receivedOrder.getExecutionDetails().executedRate())
                    .isEqualByComparingTo(new BigDecimal("3.50000000"));
            assertThat(receivedOrder.getExecutionDetails().counterparty())
                    .isEqualTo("BankCo International");
            assertThat(receivedOrder.getExecutionDetails().dealingReference()).isEqualTo(dealRef);
            assertThat(receivedOrder.getExecutionDetails().generatedContractNumber()).isEqualTo(contractNum);
        }

        @Test
        void execute_by_wrong_trader_throws() {
            assertThatThrownBy(() -> receivedOrder.execute(
                    new BigDecimal("3.50000000"), "BankCo",
                    new DealingReference("DL-x"), new ContractNumber("CN-x"),
                    TRADER_B, NOW
            )).isInstanceOf(InvalidOrderException.class);
        }
    }

    // ── Cancel ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancel")
    class Cancel {

        @Test
        void cancel_from_received_sets_cancelled() {
            receivedOrder.cancel(NOW);
            assertThat(receivedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        void cancel_from_assigned_throws() {
            receivedOrder.assign(TRADER_A, NOW);

            assertThatThrownBy(() -> receivedOrder.cancel(NOW))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    // ── Reject ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        void reject_from_received_with_reason() {
            receivedOrder.reject("Insufficient allocation", NOW);

            assertThat(receivedOrder.getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(receivedOrder.getRejectionReason()).isEqualTo("Insufficient allocation");
        }

        @Test
        void reject_from_assigned_throws() {
            receivedOrder.assign(TRADER_A, NOW);

            assertThatThrownBy(() -> receivedOrder.reject("reason", NOW))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update")
    class Update {

        @BeforeEach
        void assign() {
            receivedOrder.assign(TRADER_A, NOW);
        }

        @Test
        void update_by_assigned_trader_modifies_mutable_fields() {
            receivedOrder.update(
                    new BigDecimal("6000000.00"),
                    TODAY.plusDays(5),
                    new BigDecimal("3.50000000"),
                    "Updated preference",
                    TRADER_A, TODAY, NOW
            );

            assertThat(receivedOrder.getAmount()).isEqualByComparingTo(new BigDecimal("6000000.00"));
            assertThat(receivedOrder.getValueDate()).isEqualTo(TODAY.plusDays(5));
            assertThat(receivedOrder.getMinimumRate()).isEqualByComparingTo(new BigDecimal("3.50000000"));
            assertThat(receivedOrder.getDesiredCounterpartyComment()).isEqualTo("Updated preference");
        }

        @Test
        void update_by_wrong_trader_throws() {
            assertThatThrownBy(() -> receivedOrder.update(
                    null, null, null, null, TRADER_B, TODAY, NOW
            )).isInstanceOf(InvalidOrderException.class);
        }

        @Test
        void update_from_received_status_throws() {
            receivedOrder.unassign(TRADER_A, NOW);

            assertThatThrownBy(() -> receivedOrder.update(
                    null, null, null, null, TRADER_A, TODAY, NOW
            )).isInstanceOf(InvalidOrderException.class);
        }
    }
}
