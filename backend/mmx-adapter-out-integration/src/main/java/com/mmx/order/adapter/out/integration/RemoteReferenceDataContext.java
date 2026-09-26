package com.mmx.order.adapter.out.integration;

/**
 * Shared connection context for all remote-backed reference-data adapters: the LODH base URL and the
 * {@code X-MMX-CrossOrg-Key} transport credential that binds to the proven client principal.
 */
public record RemoteReferenceDataContext(String baseUrl, String credentialKey) {

    public RemoteReferenceDataContext {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        if (credentialKey == null || credentialKey.isBlank()) {
            throw new IllegalArgumentException("credentialKey must not be blank");
        }
    }

    public String normalizedBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
