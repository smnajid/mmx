package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidManagedCurrencyException;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class ManagedCurrency {

    private final String code;
    private final boolean active;
    private final BigDecimal minSubscriptionAmount;
    private final BigDecimal minIncreaseDecreaseAmount;
    private final Set<Tenor> enabledTenors;
    private final Set<NoticePeriod> enabledNoticePeriods;

    public ManagedCurrency(
            String code,
            boolean active,
            BigDecimal minSubscriptionAmount,
            BigDecimal minIncreaseDecreaseAmount,
            Set<Tenor> enabledTenors,
            Set<NoticePeriod> enabledNoticePeriods) {
        this.code = validateCode(code);
        this.active = active;
        this.minSubscriptionAmount = validatePositive(minSubscriptionAmount, "minSubscriptionAmount");
        this.minIncreaseDecreaseAmount = validatePositive(minIncreaseDecreaseAmount, "minIncreaseDecreaseAmount");
        this.enabledTenors = validateTenors(enabledTenors);
        this.enabledNoticePeriods = validateNoticePeriods(enabledNoticePeriods);
    }

    public static String validateCode(String code) {
        if (code == null || !code.matches("[A-Z]{3}")) {
            throw new InvalidManagedCurrencyException("Currency code must be a three-letter ISO 4217 code");
        }
        return code;
    }

    private static BigDecimal validatePositive(BigDecimal amount, String field) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidManagedCurrencyException(field + " must be greater than zero");
        }
        return amount;
    }

    private static Set<Tenor> validateTenors(Set<Tenor> tenors) {
        if (tenors == null || tenors.isEmpty()) {
            throw new InvalidManagedCurrencyException("At least one tenor must be enabled");
        }
        return EnumSet.copyOf(tenors);
    }

    private static Set<NoticePeriod> validateNoticePeriods(Set<NoticePeriod> noticePeriods) {
        if (noticePeriods == null || noticePeriods.isEmpty()) {
            throw new InvalidManagedCurrencyException("At least one notice period must be enabled");
        }
        return EnumSet.copyOf(noticePeriods);
    }

    public String getCode() {
        return code;
    }

    public boolean isActive() {
        return active;
    }

    public BigDecimal getMinSubscriptionAmount() {
        return minSubscriptionAmount;
    }

    public BigDecimal getMinIncreaseDecreaseAmount() {
        return minIncreaseDecreaseAmount;
    }

    public Set<Tenor> getEnabledTenors() {
        return EnumSet.copyOf(enabledTenors);
    }

    public Set<NoticePeriod> getEnabledNoticePeriods() {
        return EnumSet.copyOf(enabledNoticePeriods);
    }

    public ManagedCurrency withActive(boolean active) {
        return new ManagedCurrency(
                code, active, minSubscriptionAmount, minIncreaseDecreaseAmount, enabledTenors, enabledNoticePeriods);
    }

    public ManagedCurrency withRules(
            BigDecimal minSubscriptionAmount,
            BigDecimal minIncreaseDecreaseAmount,
            Set<Tenor> enabledTenors,
            Set<NoticePeriod> enabledNoticePeriods) {
        return new ManagedCurrency(
                code,
                active,
                Objects.requireNonNullElse(minSubscriptionAmount, this.minSubscriptionAmount),
                Objects.requireNonNullElse(minIncreaseDecreaseAmount, this.minIncreaseDecreaseAmount),
                Objects.requireNonNullElse(enabledTenors, this.enabledTenors),
                Objects.requireNonNullElse(enabledNoticePeriods, this.enabledNoticePeriods));
    }
}
