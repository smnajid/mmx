package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.LegalEntityCode;

import java.util.Optional;

/**
 * Binds a cross-org transport credential to exactly one remote {@link LegalEntityCode} — the
 * transport-proven principal. Implemented by the adapter that owns the credential store (per-client
 * signing keys, mTLS cert mapping, OAuth2 client-credentials, etc.).
 *
 * <p>Spec: {@code order-routing} — D8 trust boundary. The credential is the sole source of identity;
 * request payloads MUST NOT be trusted for {@code originatingLegalEntityCode}.
 */
public interface CrossOrgCredentialBinder {

    /**
     * @param credential the raw transport credential (e.g. {@code X-MMX-CrossOrg-Key} header value)
     * @return the proven {@link LegalEntityCode}, or empty if the credential is unknown/unresolvable
     */
    Optional<LegalEntityCode> bindOriginatingLegalEntity(String credential);
}
