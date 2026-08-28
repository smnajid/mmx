package com.mmx.order.domain.exception;

import com.mmx.order.domain.model.LegalEntityCode;

/**
 * Defense-in-depth domain rule: the proven {@code originatingLegalEntityCode} is not a TradingClient
 * connected to this hub. Thrown by the use case when the gateway membership check was bypassed.
 */
public class CrossOrgMembershipException extends RuntimeException {

    public CrossOrgMembershipException(LegalEntityCode proven) {
        super("Legal entity " + proven + " is not a TradingClient member of this hub");
    }
}
