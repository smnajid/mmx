package com.mmx.order.application.exception;

import com.mmx.order.domain.model.DelegatedGrantKey;

public class GrantNotFoundException extends RuntimeException {

    public GrantNotFoundException(DelegatedGrantKey key) {
        super("Delegated institution grant not found for key " + key);
    }
}
