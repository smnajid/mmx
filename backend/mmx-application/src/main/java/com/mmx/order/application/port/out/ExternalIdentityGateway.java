package com.mmx.order.application.port.out;

import com.mmx.order.domain.exception.RoutingFailure;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.PortfolioNumber;

import java.util.Optional;

/**
 * CGED's cross-org account resolver — maps {@code (client LegalEntityCode, client portfolioNumber,
 * hub LegalEntityCode) → hub-side portfolioNumber} <em>before</em> the leg-A send.
 *
 * <p>Spec: {@code order-routing} — "Remote account resolution via ExternalIdentityGateway". The
 * resolved account travels in the leg-A payload (see {@link RemoteRoutingRequest#portfolioNumber()});
 * the hub deployment trusts it and does not revalidate it, by the same principle local routing
 * trusts its own {@link GlobalAccountDirectory}. If resolution fails, the client deployment
 * transitions the client-side order to {@code Rejected} directly (no round-trip to the hub, no
 * hub-side order created).
 *
 * <p>Adapter placement: adapter over the external External Identity system in
 * {@code mmx-adapter-out-integration}; selected when the connected hub is remote
 * ({@code isRemoteHub}). Deliberately NOT a {@link GlobalAccountDirectory} implementation — it has
 * a different key (client portfolioNumber, not currency), a different location (CGED-side,
 * pre-send), and a different trust profile (design D3).
 */
public interface ExternalIdentityGateway {

    /**
     * Resolves the hub-side portfolio number for a remote route.
     *
     * @return the resolved hub-side portfolio number, or empty when no mapping exists (the caller
     *     transitions the client-side order to {@code Rejected} / may throw {@link RoutingFailure})
     */
    Optional<PortfolioNumber> resolveHubSidePortfolioNumber(
            LegalEntityCode clientLegalEntityCode,
            PortfolioNumber clientPortfolioNumber,
            LegalEntityCode hubLegalEntityCode);
}
