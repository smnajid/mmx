package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidOrderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MoneyMarketOrder creation invariants")
class MoneyMarketOrderCreationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final LocalDate VALID_VALUE_DATE = TODAY.plusDays(2);

    // ── Valid creation scenarios ─────────────────────────────────────────────

    @Nested
    @DisplayName("Valid TERM orders")
    class ValidTermOrders {

        @Test
        void term_subscription_with_tenor() {
            MoneyMarketOrder order = MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-001"),
                    OrderType.TERM,
                    OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"),
                    "EUR",
                    new BigDecimal("5000000.00"),
                    VALID_VALUE_DATE,
                    new BigDecimal("3.25000000"),
                    Tenor._3M,
                    null, null, null,
                    TODAY
            );

            assertThat(order.getId()).isNotNull();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.RECEIVED);
            assertThat(order.getOrderType()).isEqualTo(OrderType.TERM);
            assertThat(order.getOrderOperation()).isEqualTo(OrderOperation.SUBSCRIPTION);
            assertThat(order.getTenor()).isEqualTo(Tenor._3M);
            assertThat(order.getNoticePeriod()).isNull();
            assertThat(order.getAssignment()).isNull();
            assertThat(order.getExecutionDetails()).isNull();
            assertThat(order.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Valid ON_CALL orders")
    class ValidOnCallOrders {

        @Test
        void oncall_subscription_with_notice_period() {
            MoneyMarketOrder order = MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-002"),
                    OrderType.ON_CALL,
                    OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"),
                    "EUR",
                    new BigDecimal("1000000.00"),
                    VALID_VALUE_DATE,
                    new BigDecimal("2.50000000"),
                    null,
                    NoticePeriod._24H, null, null,
                    TODAY
            );

            assertThat(order.getOrderType()).isEqualTo(OrderType.ON_CALL);
            assertThat(order.getNoticePeriod()).isEqualTo(NoticePeriod._24H);
            assertThat(order.getTenor()).isNull();
        }

        @Test
        void oncall_increase_with_source_contract() {
            MoneyMarketOrder order = MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-003"),
                    OrderType.ON_CALL,
                    OrderOperation.INCREASE,
                    new PortfolioNumber("PF-001"),
                    "EUR",
                    new BigDecimal("500000.00"),
                    VALID_VALUE_DATE,
                    new BigDecimal("2.50000000"),
                    null,
                    NoticePeriod._48H,
                    new ContractNumber("CN-existing-001"),
                    null,
                    TODAY
            );

            assertThat(order.getSourceContractNumber()).isEqualTo(new ContractNumber("CN-existing-001"));
        }
    }

    // ── Invalid OrderType/OrderOperation combos ──────────────────────────────

    @Nested
    @DisplayName("TERM only allows SUBSCRIPTION")
    class TermOperationValidation {

        @Test
        void term_increase_rejected() {
            assertThatThrownBy(() -> MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.INCREASE,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("1000000.00"), VALID_VALUE_DATE,
                    new BigDecimal("3.00000000"),
                    Tenor._1M, null, new ContractNumber("CN-001"), null,
                    TODAY
            )).isInstanceOf(InvalidOrderException.class);
        }

        @Test
        void term_decrease_rejected() {
            assertThatThrownBy(() -> MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.DECREASE,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("1000000.00"), VALID_VALUE_DATE,
                    new BigDecimal("3.00000000"),
                    Tenor._1M, null, new ContractNumber("CN-001"), null,
                    TODAY
            )).isInstanceOf(InvalidOrderException.class);
        }

        @Test
        void term_redemption_rejected() {
            assertThatThrownBy(() -> MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.REDEMPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("1000000.00"), VALID_VALUE_DATE,
                    new BigDecimal("3.00000000"),
                    Tenor._1M, null, new ContractNumber("CN-001"), null,
                    TODAY
            )).isInstanceOf(InvalidOrderException.class);
        }
    }

    // ── Field requirements ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Tenor and NoticePeriod exclusivity")
    class TenorNoticePeriodExclusivity {

        @Test
        void term_without_tenor_rejected() {
            assertThatThrownBy(() -> MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("1000000.00"), VALID_VALUE_DATE,
                    new BigDecimal("3.00000000"),
                    null, null, null, null,
                    TODAY
            )).isInstanceOf(InvalidOrderException.class)
              .hasMessageContaining("Tenor");
        }

        @Test
        void term_with_notice_period_rejected() {
            assertThatThrownBy(() -> MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("1000000.00"), VALID_VALUE_DATE,
                    new BigDecimal("3.00000000"),
                    Tenor._1M, NoticePeriod._24H, null, null,
                    TODAY
            )).isInstanceOf(InvalidOrderException.class);
        }

        @Test
        void oncall_without_notice_period_rejected() {
            assertThatThrownBy(() -> MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.ON_CALL, OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("1000000.00"), VALID_VALUE_DATE,
                    new BigDecimal("3.00000000"),
                    null, null, null, null,
                    TODAY
            )).isInstanceOf(InvalidOrderException.class)
              .hasMessageContaining("NoticePeriod");
        }

        @Test
        void oncall_with_tenor_rejected() {
            assertThatThrownBy(() -> MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.ON_CALL, OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("1000000.00"), VALID_VALUE_DATE,
                    new BigDecimal("3.00000000"),
                    Tenor._1M, NoticePeriod._24H, null, null,
                    TODAY
            )).isInstanceOf(InvalidOrderException.class);
        }
    }

    @Nested
    @DisplayName("Lifecycle operations require sourceContractNumber")
    class SourceContractNumberRequirement {

        @Test
        void increase_without_source_contract_rejected() {
            assertThatThrownBy(() -> MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.ON_CALL, OrderOperation.INCREASE,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("500000.00"), VALID_VALUE_DATE,
                    new BigDecimal("2.00000000"),
                    null, NoticePeriod._24H, null, null,
                    TODAY
            )).isInstanceOf(InvalidOrderException.class)
              .hasMessageContaining("sourceContractNumber");
        }
    }

    // ── Numeric and date guards ──────────────────────────────────────────────

    @Nested
    @DisplayName("Amount and rate guards")
    class NumericGuards {

        @Test
        void zero_amount_rejected() {
            assertThatThrownBy(() -> MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    BigDecimal.ZERO, VALID_VALUE_DATE,
                    new BigDecimal("3.00000000"),
                    Tenor._1M, null, null, null,
                    TODAY
            )).isInstanceOf(InvalidOrderException.class)
              .hasMessageContaining("Amount");
        }

        @Test
        void negative_amount_rejected() {
            assertThatThrownBy(() -> MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("-1.00"), VALID_VALUE_DATE,
                    new BigDecimal("3.00000000"),
                    Tenor._1M, null, null, null,
                    TODAY
            )).isInstanceOf(InvalidOrderException.class);
        }

        @Test
        void negative_minimum_rate_rejected() {
            assertThatThrownBy(() -> MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("1000000.00"), VALID_VALUE_DATE,
                    new BigDecimal("-0.00000001"),
                    Tenor._1M, null, null, null,
                    TODAY
            )).isInstanceOf(InvalidOrderException.class)
              .hasMessageContaining("MinimumRate");
        }

        @Test
        void zero_minimum_rate_is_valid() {
            MoneyMarketOrder order = MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("1000000.00"), VALID_VALUE_DATE,
                    BigDecimal.ZERO.setScale(8),
                    Tenor._1M, null, null, null,
                    TODAY
            );
            assertThat(order.getMinimumRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void null_minimum_rate_allowed() {
            MoneyMarketOrder order = MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("1000000.00"), VALID_VALUE_DATE,
                    null,
                    Tenor._1M, null, null, null,
                    TODAY
            );
            assertThat(order.getMinimumRate()).isNull();
        }

        @Test
        void bigdecimal_precision_preserved() {
            MoneyMarketOrder order = MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("5000000.00"), VALID_VALUE_DATE,
                    new BigDecimal("3.25000000"),
                    Tenor._3M, null, null, null,
                    TODAY
            );
            assertThat(order.getAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
            assertThat(order.getMinimumRate()).isEqualByComparingTo(new BigDecimal("3.25000000"));
        }
    }

    @Nested
    @DisplayName("ValueDate must be at least today+2")
    class ValueDateGuard {

        @Test
        void value_date_today_rejected() {
            assertThatThrownBy(() -> MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("1000000.00"), TODAY,
                    new BigDecimal("3.00000000"),
                    Tenor._1M, null, null, null,
                    TODAY
            )).isInstanceOf(InvalidOrderException.class)
              .hasMessageContaining("ValueDate");
        }

        @Test
        void value_date_tomorrow_rejected() {
            assertThatThrownBy(() -> MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("1000000.00"), TODAY.plusDays(1),
                    new BigDecimal("3.00000000"),
                    Tenor._1M, null, null, null,
                    TODAY
            )).isInstanceOf(InvalidOrderException.class);
        }

        @Test
        void value_date_today_plus_two_is_valid() {
            MoneyMarketOrder order = MoneyMarketOrder.create(
                    new ExternalOrderReference("PM-X"),
                    OrderType.TERM, OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-001"), "EUR",
                    new BigDecimal("1000000.00"), TODAY.plusDays(2),
                    new BigDecimal("3.00000000"),
                    Tenor._1M, null, null, null,
                    TODAY
            );
            assertThat(order.getValueDate()).isEqualTo(TODAY.plusDays(2));
        }
    }
}
