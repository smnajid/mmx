package com.mmx.order.adapter.in.rest.crossorg;

/**
 * Thrown when the transport-proven principal is not a TradingClient member of this hub. Mapped to
 * HTTP 403 by {@link CrossOrgExceptionHandler}.
 *
 * <p>Spec: {@code order-routing} — D8 trust boundary, gateway early-reject.
 */
public class ForbiddenCrossOrgMembershipException extends RuntimeException {

    public ForbiddenCrossOrgMembershipException(String message) {
        super(message);
    }
}
