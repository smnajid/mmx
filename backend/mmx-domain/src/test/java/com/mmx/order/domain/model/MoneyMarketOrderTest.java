package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyMarketOrderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final Instant T0 = Instant.parse("2026-05-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-05-01T11:00:00Z");

    @Test
    void markAccounted_fromExecuted_setsAccountedAndUpdatedAt() {
        MoneyMarketOrder order = executedOrder();
        order.markAccounted(T1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCOUNTED);
        assertThat(order.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void markAccounted_rejectsNullNow() {
        MoneyMarketOrder order = executedOrder();
        assertThatThrownBy(() -> order.markAccounted(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("now");
    }

    @Test
    void markAccounted_rejectsNonExecutedStatuses() {
        MoneyMarketOrder received = createReceived();
        assertThatThrownBy(() -> received.markAccounted(T1))
                .isInstanceOf(InvalidStatusTransitionException.class);

        MoneyMarketOrder assigned = createReceived();
        assigned.assign(new TraderId("t-a"), T0);
        assertThatThrownBy(() -> assigned.markAccounted(T1))
                .isInstanceOf(InvalidStatusTransitionException.class);

        MoneyMarketOrder cancelled = createReceived();
        cancelled.cancel(T0);
        assertThatThrownBy(() -> cancelled.markAccounted(T1))
                .isInstanceOf(InvalidStatusTransitionException.class);

        MoneyMarketOrder rejected = createReceived();
        rejected.reject(new TraderId("any"), "No capacity", T0);
        assertThatThrownBy(() -> rejected.markAccounted(T1))
                .isInstanceOf(InvalidStatusTransitionException.class);

        MoneyMarketOrder alreadyAccounted = executedOrder();
        alreadyAccounted.markAccounted(T1);
        assertThatThrownBy(() -> alreadyAccounted.markAccounted(T1))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    private static MoneyMarketOrder createReceived() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("REF-MK-" + System.nanoTime()),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-1"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                new BigDecimal("3.25000000"),
                Tenor._3M,
                null,
                null,
                null,
                TODAY);
    }

    private static MoneyMarketOrder executedOrder() {
        MoneyMarketOrder order = createReceived();
        order.assign(new TraderId("t-a"), T0);
        order.execute(
                new BigDecimal("3.5"),
                "BankCo",
                "HSBC-01",
                new DealingReference("DL-1"),
                new ContractNumber("CN-1"),
                new TraderId("t-a"),
                T0);
        return order;
    }
}
