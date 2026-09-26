package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.HubLocality;
import com.mmx.order.domain.model.LegalEntityCode;

/**
 * Classifies a hub-side routed pair by the locality of its originating client: {@link HubLocality#LOCAL}
 * when the originating LegalEntity belongs to this deployment's Organisation (the client-side order is
 * in-process — synchronous propagation applies), {@link HubLocality#REMOTE} when it belongs to a
 * foreign Organisation (cross-deployment pair — outcomes mirror via the leg-B
 * {@link RoutingOutcomeOutbox}, silence is never terminal). Derived at transition time, never stored.
 *
 * <p>V1 rule: compare the originating LegalEntity's OrganisationCode with this deployment's
 * OrganisationCode. Spec: {@code order-routing} — outcome propagation; CONTEXT "HubLocality",
 * "Routing outcome propagation".
 */
@FunctionalInterface
public interface RoutedPairLocalityResolver {

    HubLocality resolve(LegalEntityCode originatingLegalEntityCode);
}
