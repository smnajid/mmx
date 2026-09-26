package com.mmx.order.adapter.in.rest.crossorg;

import com.mmx.order.application.port.out.CrossOrgCredentialBinder;
import com.mmx.order.application.port.out.CrossOrgMembershipPort;
import com.mmx.order.domain.model.LegalEntityCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Resolves the transport-proven {@link LegalEntityCode} principal from the {@code X-MMX-CrossOrg-Key}
 * credential, then early-rejects non-members. Throws {@link UnauthorizedCrossOrgException} (→ 401)
 * when the credential is missing or unresolvable; {@link ForbiddenCrossOrgMembershipException}
 * (→ 403) when the proven principal is not a TradingClient member of this hub.
 *
 * <p>Spec: {@code order-routing} — D8 trust boundary. Identity is transport-proven; request payloads
 * are never trusted for {@code originatingLegalEntityCode}.
 */
@Component
@ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "hub")
public class CrossOrgIdentityResolver {

    private final CrossOrgCredentialBinder credentialBinder;
    private final CrossOrgMembershipPort membershipPort;

    public CrossOrgIdentityResolver(
            CrossOrgCredentialBinder credentialBinder, CrossOrgMembershipPort membershipPort) {
        this.credentialBinder = Objects.requireNonNull(credentialBinder, "credentialBinder");
        this.membershipPort = Objects.requireNonNull(membershipPort, "membershipPort");
    }

    public LegalEntityCode resolve(String credential) {
        if (credential == null || credential.isBlank()) {
            throw new UnauthorizedCrossOrgException("Missing transport credential");
        }
        LegalEntityCode proven =
                credentialBinder
                        .bindOriginatingLegalEntity(credential)
                        .orElseThrow(
                                () -> new UnauthorizedCrossOrgException("Unresolvable transport credential"));
        if (!membershipPort.isRemoteTradingClientOfThisHub(proven)) {
            throw new ForbiddenCrossOrgMembershipException(
                    "Legal entity " + proven + " is not a TradingClient member of this hub");
        }
        return proven;
    }
}
