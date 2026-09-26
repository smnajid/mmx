package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.Tenor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Leg-A payload for a remote routed order — sent from the client deployment (CGED) to the hub
 * deployment (LODH) via {@link RemoteRoutingGateway}.
 *
 * <p>Spec: {@code order-routing} — cross-org routing transport backbone. The request carries:
 *
 * <ul>
 *   <li>{@code originatingLegalEntityCode} — the client LegalEntityCode, used as the cross-boundary
 *       correlation key with {@code routingId}. NOT a payload-claimed identity — the hub binds the
 *       same value from the transport credential at the gateway (defense-in-depth).
 *   <li>{@code routingId} — the CGED-minted deterministic routing id; LODH trusts it and uses
 *       {@code (originatingLegalEntityCode, routingId)} as its hub-side idempotency key.
 *   <li>{@code portfolioNumber} — the RESOLVED hub-side account (from {@code ExternalIdentityGateway});
 *       travels in the payload; LODH trusts it and does not revalidate.
 *   <li>{@code institutionCode} — the HUB-NATIVE institution code (proxy indirection collapses; the
 *       "{hub} via {client}" rendering is display-only, client-side).
 *   <li>{@code originatingExternalOrderReference} — for traceability on the hub-side order; not used
 *       as the idempotency key.
 *   <li>The order fields (currency, amount, valueDate, type, operation, tenor|noticePeriod,
 *       minimumRate, sourceContractNumber).
 * </ul>
 *
 * <p>No deployment-internal order UUID crosses the boundary.
 */
public record RemoteRoutingRequest(
        LegalEntityCode originatingLegalEntityCode,
        RoutingId routingId,
        PortfolioNumber portfolioNumber,
        String institutionCode,
        ExternalOrderReference originatingExternalOrderReference,
        String currency,
        BigDecimal amount,
        LocalDate valueDate,
        OrderType orderType,
        OrderOperation orderOperation,
        Tenor tenor,
        NoticePeriod noticePeriod,
        BigDecimal minimumRate,
        ContractNumber sourceContractNumber) {

    public RemoteRoutingRequest {
        Objects.requireNonNull(originatingLegalEntityCode, "originatingLegalEntityCode must not be null");
        Objects.requireNonNull(routingId, "routingId must not be null");
        Objects.requireNonNull(portfolioNumber, "portfolioNumber must not be null");
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new IllegalArgumentException("institutionCode must not be blank");
        }
        Objects.requireNonNull(originatingExternalOrderReference, "originatingExternalOrderReference must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(valueDate, "valueDate must not be null");
        Objects.requireNonNull(orderType, "orderType must not be null");
        Objects.requireNonNull(orderOperation, "orderOperation must not be null");
    }
}
