package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import com.mmx.order.domain.exception.UnauthorizedTraderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@Tag("fast")

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
                new LegalEntityCode("LOC"),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-001"),
                "EUR",
                new BigDecimal("5000000.00"),
                TODAY.plusDays(2),
                new BigDecimal("3.25000000"),
                Tenor._3M,
                null, null, "BNKCO", "BankCo",
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
                    new BigDecimal("3.50000000"), "BankCo", "HSBC-01",
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
                    .isInstanceOf(UnauthorizedTraderException.class);
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
                    new BigDecimal("3.50000000"), "BankCo International", "HSBC-01",
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
                    new BigDecimal("3.50000000"), "BankCo", "HSBC-01",
                    new DealingReference("DL-x"), new ContractNumber("CN-x"),
                    TRADER_B, NOW
            )).isInstanceOf(UnauthorizedTraderException.class);
        }

        @Test
        void execute_lifecycle_with_null_contract_parameter_throws() {
            MoneyMarketOrder increase =
                    MoneyMarketOrder.create(
                            new ExternalOrderReference("PM-LC-NULL-CN"),
                            new LegalEntityCode("LOC"),
                            OrderType.ON_CALL,
                            OrderOperation.INCREASE,
                            new PortfolioNumber("PF-001"),
                            "EUR",
                            new BigDecimal("5000000.00"),
                            TODAY.plusDays(2),
                            null,
                            null,
                            NoticePeriod._24H,
                            new ContractNumber("CN-SRC"), "BNKCO", "BankCo",
                            TODAY);
            increase.assign(TRADER_A, NOW);

            assertThatThrownBy(
                            () ->
                                    increase.execute(
                                            new BigDecimal("3.50000000"),
                                            "BankCo International",
                                        "HSBC-01",
                                            new DealingReference("DL-x"),
                                            null,
                                            TRADER_A,
                                            NOW))
                    .isInstanceOf(InvalidOrderException.class)
                    .hasMessageContaining("sourceContractNumber");
        }

        @Test
        void execute_below_minimum_rate_throws_when_floor_present() {
            assertThatThrownBy(() -> receivedOrder.execute(
                    new BigDecimal("3.24000000"), "BankCo", "HSBC-01",
                    new DealingReference("DL-low"), new ContractNumber("CN-low"),
                    TRADER_A, NOW
            )).isInstanceOf(InvalidOrderException.class)
                    .hasMessageContaining("MinimumRate");
        }

        @Test
        void execute_succeeds_when_no_minimum_floor() {
            MoneyMarketOrder openFloor = MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-NO-FLOOR"),
                    new LegalEntityCode("LOC"),
                    OrderType.TERM,
                    OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"),
                    "EUR",
                    new BigDecimal("5000000.00"),
                    TODAY.plusDays(2),
                    null,
                    Tenor._3M, null, null, "BNKCO", "BankCo",
                    TODAY
            );
            openFloor.assign(TRADER_A, NOW);
            openFloor.execute(
                    new BigDecimal("0.01000000"), "BankCo", "HSBC-01",
                    new DealingReference("DL-any"), new ContractNumber("CN-any"),
                    TRADER_A, NOW
            );
            assertThat(openFloor.getStatus()).isEqualTo(OrderStatus.EXECUTED);
        }

        @Test
        void execute_from_received_without_assign_throws_invalid_transition() {
            MoneyMarketOrder receivedOnly = MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-EXEC-NO-ASSIGN"),
                    new LegalEntityCode("LOC"),
                    OrderType.TERM,
                    OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"),
                    "EUR",
                    new BigDecimal("5000000.00"),
                    TODAY.plusDays(2),
                    new BigDecimal("3.25000000"),
                    Tenor._3M, null, null, "BNKCO", "BankCo",
                    TODAY
            );
            assertThatThrownBy(
                            () ->
                                    receivedOnly.execute(
                                            new BigDecimal("3.50000000"),
                                            "BankCo",
                                    "HSBC-01",
                                            new DealingReference("DL-x"),
                                            new ContractNumber("CN-x"),
                                            TRADER_A,
                                            NOW))
                    .isInstanceOf(InvalidStatusTransitionException.class);
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
        void reject_from_received_with_reason_any_trader() {
            receivedOrder.reject(TRADER_B, "Insufficient allocation", NOW);

            assertThat(receivedOrder.getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(receivedOrder.getRejectionReason()).isEqualTo("Insufficient allocation");
        }

        @Test
        void reject_from_assigned_by_assignee_success() {
            receivedOrder.assign(TRADER_A, NOW);
            receivedOrder.reject(TRADER_A, "Market below PM floor", NOW);

            assertThat(receivedOrder.getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(receivedOrder.getRejectionReason()).isEqualTo("Market below PM floor");
        }

        @Test
        void reject_from_assigned_by_other_trader_throws() {
            receivedOrder.assign(TRADER_A, NOW);

            assertThatThrownBy(() -> receivedOrder.reject(TRADER_B, "Cannot meet terms", NOW))
                    .isInstanceOf(UnauthorizedTraderException.class);
        }

        @Test
        void reject_requires_non_blank_reason() {
            assertThatThrownBy(() -> receivedOrder.reject(TRADER_A, "   ", NOW))
                    .isInstanceOf(InvalidOrderException.class);
        }

        @Test
        void reject_from_executed_throws() {
            receivedOrder.assign(TRADER_A, NOW);
            receivedOrder.execute(
                    new BigDecimal("3.50000000"), "BankCo", "HSBC-01",
                    new DealingReference("DL-z"), new ContractNumber("CN-z"),
                    TRADER_A, NOW
            );

            assertThatThrownBy(() -> receivedOrder.reject(TRADER_A, "Late change", NOW))
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
        void update_by_assigned_trader_modifies_amount_and_value_date_minimum_unchanged() {
            BigDecimal originalMin = receivedOrder.getMinimumRate();
            receivedOrder.update(
                    new BigDecimal("6000000.00"),
                    TODAY.plusDays(5),
                    TRADER_A, TODAY, NOW
            );

            assertThat(receivedOrder.getAmount()).isEqualByComparingTo(new BigDecimal("6000000.00"));
            assertThat(receivedOrder.getValueDate()).isEqualTo(TODAY.plusDays(5));
            assertThat(receivedOrder.getMinimumRate()).isEqualByComparingTo(originalMin);
        }

        @Test
        void update_by_wrong_trader_throws() {
            assertThatThrownBy(() -> receivedOrder.update(
                    null, null, TRADER_B, TODAY, NOW
            )).isInstanceOf(UnauthorizedTraderException.class);
        }

        @Test
        void update_from_received_status_throws() {
            receivedOrder.unassign(TRADER_A, NOW);

            assertThatThrownBy(() -> receivedOrder.update(
                    null, null, TRADER_A, TODAY, NOW
            )).isInstanceOf(InvalidStatusTransitionException.class);
        }
    }
}
