package com.mmx.order.application.port.in;

import com.mmx.order.application.port.out.RemoteRoutingRequest;
import com.mmx.order.application.port.out.RemoteRoutingResponse;
import com.mmx.order.domain.model.LegalEntityCode;

/**
 * Hub-deployment (LODH) leg-A inbound use case: accept or reject a remote routed order
 * synchronously. Distinct from the PM {@code IntakeUseCase} because a routed inbound order's
 * authority is the <strong>grant</strong> (not the hub's own intake enablement).
 *
 * <p>Spec: {@code order-routing} — remote routed order intake at the hub. The use case receives a
 * <strong>transport-proven</strong> {@code originatingLegalEntityCode} (bound from the leg-A
 * credential at the gateway) and never trusts a payload-claimed identity. It validates
 * {@code (institution, currency, tenor|noticePeriod)} against the originating client's grant using
 * the hub's own reference data; on success it creates the hub-side order in {@code RECEIVED}, emits a
 * leg-B {@code ACCEPTED} outbox row in the same transaction, and returns {@link RemoteRoutingResponse.Accept};
 * on a grant/currency/tenor validation failure it returns {@link RemoteRoutingResponse.Reject},
 * creates no hub-side order, and emits no event. It does NOT resolve the global account (it travels
 * in the payload) and does NOT revalidate the supplied {@code portfolioNumber}.
 *
 * <p>Cross-boundary idempotency: the hub trusts the client-minted {@code routingId}; the composite
 * key {@code (originatingLegalEntityCode, routingId)} is enforced by a partial unique index. A leg-A
 * retry that collides resolves to the already-persisted hub-side order and returns the same accept.
 */
public interface AcceptRoutedHubOrderUseCase {

    /**
     * @param request the leg-A payload (order fields, resolved {@code portfolioNumber}, hub-native
     *     {@code institutionCode}, {@code routingId}, and a payload-carried
     *     {@code originatingLegalEntityCode} which is NOT trusted)
     * @param provenOriginatingLegalEntityCode the client LegalEntityCode, proven from the transport
     *     credential by the gateway; authoritative for grant lookup, membership, hub-side order
     *     attribution, and the idempotency key
     */
    RemoteRoutingResponse accept(RemoteRoutingRequest request, LegalEntityCode provenOriginatingLegalEntityCode);
}
