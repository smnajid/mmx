package com.mmx.order.domain.exception;

import com.mmx.order.domain.model.DelegatedGrantKey;

public class DuplicateDelegatedGrantException extends RuntimeException {

    public DuplicateDelegatedGrantException(DelegatedGrantKey key) {
        super("Delegated institution grant already exists for key " + key);
    }
}
