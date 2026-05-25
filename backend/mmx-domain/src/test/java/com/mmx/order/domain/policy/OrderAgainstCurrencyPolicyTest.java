package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OpenContractPosition;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderAgainstCurrencyPolicyTest {

    private static final BigDecimal MIN_SUB = new BigDecimal("1000000.00");
    private static final BigDecimal MIN_LIFE = new BigDecimal("250000.00");

    private OrderAgainstCurrencyPolicy policy;
    private ManagedCurrency eur;

    @BeforeEach
    void setUp() {
        policy = new OrderAgainstCurrencyPolicy();
        eur =
                new ManagedCurrency(
                        "EUR",
                        true,
                        MIN_SUB,
                        MIN_LIFE,
                        EnumSet.allOf(Tenor.class),
                        EnumSet.allOf(NoticePeriod.class));
    }

    @Test
    void rejectsUnknownCurrency() {
        assertThatThrownBy(
                        () ->
                                policy.validateReceive(
                                        Optional.empty(),
                                        "EUR",
                                        OrderType.TERM,
                                        OrderOperation.SUBSCRIPTION,
                                        MIN_SUB,
                                        Tenor._3M,
                                        null,
                                        Optional.empty()))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("not managed");
    }

    @Test
    void rejectsInactiveCurrency() {
        ManagedCurrency inactive = eur.withActive(false);
        assertThatThrownBy(
                        () ->
                                policy.validateReceive(
                                        Optional.of(inactive),
                                        "EUR",
                                        OrderType.TERM,
                                        OrderOperation.SUBSCRIPTION,
                                        MIN_SUB,
                                        Tenor._3M,
                                        null,
                                        Optional.empty()))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void rejectsDisabledTenor() {
        ManagedCurrency only1m =
                new ManagedCurrency(
                        "EUR",
                        true,
                        MIN_SUB,
                        MIN_LIFE,
                        EnumSet.of(Tenor._1M),
                        EnumSet.allOf(NoticePeriod.class));
        assertThatThrownBy(
                        () ->
                                policy.validateReceive(
                                        Optional.of(only1m),
                                        "EUR",
                                        OrderType.TERM,
                                        OrderOperation.SUBSCRIPTION,
                                        MIN_SUB,
                                        Tenor._3M,
                                        null,
                                        Optional.empty()))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Tenor");
    }

    @Test
    void rejectsSubscriptionBelowMinimum() {
        assertThatThrownBy(
                        () ->
                                policy.validateReceive(
                                        Optional.of(eur),
                                        "EUR",
                                        OrderType.TERM,
                                        OrderOperation.SUBSCRIPTION,
                                        new BigDecimal("100.00"),
                                        Tenor._3M,
                                        null,
                                        Optional.empty()))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("below the minimum");
    }

    @Test
    void rejectsDecreaseBelowSubscriptionFloor() {
        var position =
                new OpenContractPosition(
                        new ContractNumber("C-1"),
                        "EUR",
                        new BigDecimal("1500000.00"));
        assertThatThrownBy(
                        () ->
                                policy.validateReceive(
                                        Optional.of(eur),
                                        "EUR",
                                        OrderType.ON_CALL,
                                        OrderOperation.DECREASE,
                                        new BigDecimal("600000.00"),
                                        null,
                                        NoticePeriod._24H,
                                        Optional.of(position)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("subscription minimum");
    }

    @Test
    void rejectsDecreaseWhenPositionMissing() {
        assertThatThrownBy(
                        () ->
                                policy.validateReceive(
                                        Optional.of(eur),
                                        "EUR",
                                        OrderType.ON_CALL,
                                        OrderOperation.DECREASE,
                                        MIN_LIFE,
                                        null,
                                        NoticePeriod._24H,
                                        Optional.empty()))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("position not found");
    }

    @Test
    void acceptsValidSubscription() {
        policy.validateReceive(
                Optional.of(eur),
                "EUR",
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                MIN_SUB,
                Tenor._3M,
                null,
                Optional.empty());
    }
}
