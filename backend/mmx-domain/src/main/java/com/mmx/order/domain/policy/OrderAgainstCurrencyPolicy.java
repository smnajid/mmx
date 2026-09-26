package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OpenContractPosition;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.Tenor;

import java.math.BigDecimal;
import java.util.Optional;

public final class OrderAgainstCurrencyPolicy {

    public void validateReceive(
            Optional<ManagedCurrency> currencyOpt,
            String orderCurrency,
            OrderType orderType,
            OrderOperation orderOperation,
            BigDecimal amount,
            Tenor tenor,
            NoticePeriod noticePeriod,
            Optional<OpenContractPosition> openPosition) {
        ManagedCurrency currency = requireActiveCurrency(currencyOpt, orderCurrency);
        validateTenorNotice(currency, orderType, tenor, noticePeriod);
        validateAmountMinimum(currency, orderOperation, amount);
        if (orderOperation == OrderOperation.DECREASE) {
            validateDecreaseSubscriptionFloor(currency, amount, openPosition, orderCurrency);
        }
    }

    public void validateAmountUpdate(
            Optional<ManagedCurrency> currencyOpt,
            String orderCurrency,
            OrderType orderType,
            OrderOperation orderOperation,
            BigDecimal newAmount,
            Tenor tenor,
            NoticePeriod noticePeriod,
            Optional<OpenContractPosition> openPositionForDecrease) {
        ManagedCurrency currency = requireActiveCurrency(currencyOpt, orderCurrency);
        validateTenorNotice(currency, orderType, tenor, noticePeriod);
        validateAmountMinimum(currency, orderOperation, newAmount);
        if (orderOperation == OrderOperation.DECREASE) {
            validateDecreaseSubscriptionFloor(currency, newAmount, openPositionForDecrease, orderCurrency);
        }
    }

    private static ManagedCurrency requireActiveCurrency(Optional<ManagedCurrency> currencyOpt, String orderCurrency) {
        if (currencyOpt.isEmpty()) {
            throw new InvalidOrderException("Currency " + orderCurrency + " is not managed by the desk");
        }
        ManagedCurrency currency = currencyOpt.get();
        if (!currency.isActive()) {
            throw new InvalidOrderException("Currency " + orderCurrency + " is not active for new orders");
        }
        if (!currency.getCode().equals(orderCurrency)) {
            throw new InvalidOrderException("Currency mismatch for managed configuration");
        }
        return currency;
    }

    private static void validateTenorNotice(
            ManagedCurrency currency, OrderType orderType, Tenor tenor, NoticePeriod noticePeriod) {
        if (orderType == OrderType.TERM) {
            if (tenor == null || !currency.getEnabledTenors().contains(tenor)) {
                throw new InvalidOrderException("Tenor is not enabled for currency " + currency.getCode());
            }
        } else {
            if (noticePeriod == null || !currency.getEnabledNoticePeriods().contains(noticePeriod)) {
                throw new InvalidOrderException(
                        "Notice period is not enabled for currency " + currency.getCode());
            }
        }
    }

    private static void validateAmountMinimum(
            ManagedCurrency currency, OrderOperation orderOperation, BigDecimal amount) {
        BigDecimal minimum =
                orderOperation == OrderOperation.SUBSCRIPTION
                        ? currency.getMinSubscriptionAmount()
                        : currency.getMinIncreaseDecreaseAmount();
        if (amount.compareTo(minimum) < 0) {
            throw new InvalidOrderException(
                    "Amount is below the minimum for " + orderOperation + " on currency " + currency.getCode());
        }
    }

    private static void validateDecreaseSubscriptionFloor(
            ManagedCurrency currency,
            BigDecimal decreaseAmount,
            Optional<OpenContractPosition> openPosition,
            String orderCurrency) {
        OpenContractPosition position =
                openPosition.orElseThrow(
                        () -> new InvalidOrderException("Open contract position not found for Decrease validation"));
        if (!position.currency().equals(orderCurrency)) {
            throw new InvalidOrderException("Contract position currency does not match order currency");
        }
        BigDecimal remaining = position.outstandingAmount().subtract(decreaseAmount);
        if (remaining.compareTo(currency.getMinSubscriptionAmount()) < 0) {
            throw new InvalidOrderException(
                    "Decrease would leave contract balance below the subscription minimum for "
                            + currency.getCode());
        }
    }
}
