package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidInstitutionException;

import java.util.Optional;

/**
 * The Term and OnCall counterparty accounts a LegalEntity holds for one institution. Each is optional;
 * when present it is a trimmed, non-blank reference of at most 34 characters (the IBAN upper bound),
 * with no currency dimension.
 */
public record CounterpartyAccounts(Optional<String> term, Optional<String> onCall) {

    static final int MAX_LENGTH = 34;

    private static final CounterpartyAccounts NONE = new CounterpartyAccounts(Optional.empty(), Optional.empty());

    public static CounterpartyAccounts none() {
        return NONE;
    }

    /** {@code null} means unset; a blank or over-long value is rejected. */
    public static CounterpartyAccounts of(String term, String onCall) {
        return new CounterpartyAccounts(
                validate("termCounterpartyAccount", term), validate("onCallCounterpartyAccount", onCall));
    }

    public Optional<String> accountFor(OrderType orderType) {
        return switch (orderType) {
            case TERM -> term;
            case ON_CALL -> onCall;
        };
    }

    private static Optional<String> validate(String field, String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidInstitutionException(field + " must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidInstitutionException(field + " must not exceed " + MAX_LENGTH + " characters");
        }
        return Optional.of(trimmed);
    }
}
