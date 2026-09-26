package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.MissingCounterpartyAccountException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.OrderType;

import java.util.Optional;

/**
 * Every order books against the owning LegalEntity's counterparty account for its OrderType (Term →
 * Term account, OnCall → OnCall account), for all four operations. The returned account is the
 * counterparty account snapshot.
 */
public final class CounterpartyAccountPolicy {

    private CounterpartyAccountPolicy() {}

    public static String requireAccountFor(Institution institution, OrderType orderType) {
        return institution
                .getCounterpartyAccounts()
                .accountFor(orderType)
                .orElseThrow(() -> new MissingCounterpartyAccountException(missingAccount(institution, orderType)));
    }

    /** Why an order of {@code orderType} is refused on {@code institution} for lack of its account, if it is. */
    public static Optional<String> refusal(Institution institution, OrderType orderType) {
        return institution.getCounterpartyAccounts().accountFor(orderType).isPresent()
                ? Optional.empty()
                : Optional.of(missingAccount(institution, orderType));
    }

    private static String missingAccount(Institution institution, OrderType orderType) {
        return "Institution " + institution.getInstitutionCode() + " has no " + label(orderType) + " counterparty account";
    }

    public static String label(OrderType orderType) {
        return switch (orderType) {
            case TERM -> "Term";
            case ON_CALL -> "OnCall";
        };
    }
}
