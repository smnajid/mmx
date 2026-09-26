package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.MissingCounterpartyAccountException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.OrderType;

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
                .orElseThrow(
                        () -> new MissingCounterpartyAccountException(
                                "Institution " + institution.getInstitutionCode() + " has no "
                                        + label(orderType) + " counterparty account"));
    }

    public static String label(OrderType orderType) {
        return switch (orderType) {
            case TERM -> "Term";
            case ON_CALL -> "OnCall";
        };
    }
}
