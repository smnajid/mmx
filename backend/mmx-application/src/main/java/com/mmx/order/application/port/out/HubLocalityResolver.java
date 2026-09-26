package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.HubLocality;
import com.mmx.order.domain.model.LegalEntityCode;

/**
 * Resolves whether a TradingClient's connected hub is {@link HubLocality#LOCAL} or
 * {@link HubLocality#REMOTE} relative to this deployment. Used by the intake path to dispatch
 * between synchronous in-process routing (local) and cross-deployment eventually-consistent
 * routing (remote).
 *
 * <p>V1 simplification: a hub deployment always resolves LOCAL; a client deployment always
 * resolves REMOTE. Future versions may enrich this with a lookup when a deployment hosts both
 * local and foreign hubs.
 */
@FunctionalInterface
public interface HubLocalityResolver {
    HubLocality resolveForClient(LegalEntityCode clientLegalEntityCode);
}
