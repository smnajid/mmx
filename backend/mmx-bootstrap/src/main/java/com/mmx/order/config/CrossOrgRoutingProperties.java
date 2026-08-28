package com.mmx.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.Set;

/**
 * Cross-org routing configuration: transport endpoints, credentials, retry/circuit-breaker policy,
 * and the credential-to-legal-entity binding store. Bound from {@code mmx.cross-org.*} properties.
 *
 * <p>Each deployment is provisioned with:
 * <ul>
 *   <li>{@code role} — {@code hub} (LODH), {@code client} (CGED), or absent (local-only)</li>
 *   <li>{@code hub-legal-entity-code} — this hub's LegalEntityCode (hub-side only, e.g. {@code LOC})</li>
 *   <li>{@code remote-routing-gateway.base-url} — LODH inbound endpoint (CGED-side)</li>
 *   <li>{@code remote-routing-gateway.credential-key} — this deployment's transport credential</li>
 *   <li>{@code external-identity.base-url} — external identity system endpoint (CGED-side)</li>
 *   <li>{@code credentials} — map of credential → LegalEntityCode (LODH-side)</li>
 *   <li>{@code retry.*} — gateway retry/circuit-breaker parameters</li>
 *   <li>{@code reference-data-remote} — use remote-backed reference-data adapters (client-side)</li>
 *   <li>{@code own-legal-entity-codes} — this client deployment's LegalEntities; the leg-B consumer
 *       filters {@code originatingLegalEntityCode ∈ own-legal-entity-codes} (client-side)</li>
 *   <li>{@code outcome-topic} — the hub-owned, org-suffixed leg-B topic to consume
 *       (e.g. {@code mmx.routed-order-outcome.LODH}); client-side</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "mmx.cross-org")
public record CrossOrgRoutingProperties(
        String role,
        String hubLegalEntityCode,
        Endpoint remoteRoutingGateway,
        Endpoint externalIdentity,
        Map<String, String> credentials,
        Retry retry,
        boolean referenceDataRemote,
        Set<String> ownLegalEntityCodes,
        String outcomeTopic) {

    public record Endpoint(String baseUrl, String credentialKey) {}

    public record Retry(
            int maxAttempts,
            long initialBackoffMs,
            int failureThreshold,
            long recoveryDurationMs) {}

    public boolean isHub() {
        return "hub".equalsIgnoreCase(role);
    }

    public boolean isClient() {
        return "client".equalsIgnoreCase(role);
    }
}
