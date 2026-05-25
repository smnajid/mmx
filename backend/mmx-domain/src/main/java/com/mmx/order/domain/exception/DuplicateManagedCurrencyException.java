package com.mmx.order.domain.exception;

public class DuplicateManagedCurrencyException extends InvalidManagedCurrencyException {

    public DuplicateManagedCurrencyException(String code) {
        super("Currency " + code + " is already managed");
    }
}
