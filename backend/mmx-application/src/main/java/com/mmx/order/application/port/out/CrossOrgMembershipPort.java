package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.LegalEntityCode;

/**
 * Defense-in-depth membership check: verifies that the transport-proven {@link LegalEntityCode} is a
 * TradingClient connected to this hub. Checked at the gateway (early-reject → 403) and re-checked
 * inside the use case (domain rule → 403).
 *
 * <p>Spec: {@code order-routing} — D8 trust boundary. The allow-list IS TradingClient membership.
 */
public interface CrossOrgMembershipPort {

    /**
     * @param code the transport-proven principal
     * @return true if the legal entity is a TradingClient whose {@code connectedHubCode} matches this
     *     hub's code
     */
    boolean isRemoteTradingClientOfThisHub(LegalEntityCode code);
}
