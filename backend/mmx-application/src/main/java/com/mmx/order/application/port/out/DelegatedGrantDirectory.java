package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.policy.NewBusinessPolicy;

/**
 * Out-port that resolves whether a TradingClient order is permitted by its delegated institution grant.
 * Given a {@code (clientLegalEntityCode, onboardedInstitutionCode, currency)}, a proposed term and the
 * {@link OrderOperation}: a Subscription or Increase needs an active grant whose enabled set contains the
 * term; a Decrease or Redemption against an existing contract is permitted regardless of the grant's state
 * ({@link GrantResolution#NOT_REQUIRED}).
 *
 * <p>The port is the single seam consumed by TradingClient intake validation and order routing. Adapters
 * implement only the grant lookups; the operation rule lives here so every adapter applies it alike. V1 is
 * backed by the {@code delegated_institution_grant} reference data; the adapter maps the client's onboarded
 * {@code institutionCode} to the hub native institution referenced by the grant.
 */
public interface DelegatedGrantDirectory {

    default GrantResolution resolveTenor(
            LegalEntityCode clientLegalEntityCode,
            String onboardedInstitutionCode,
            String currency,
            Tenor tenor,
            OrderOperation operation) {
        return NewBusinessPolicy.addsExposure(operation)
                ? lookupTenor(clientLegalEntityCode, onboardedInstitutionCode, currency, tenor)
                : GrantResolution.NOT_REQUIRED;
    }

    default GrantResolution resolveNotice(
            LegalEntityCode clientLegalEntityCode,
            String onboardedInstitutionCode,
            String currency,
            NoticePeriod noticePeriod,
            OrderOperation operation) {
        return NewBusinessPolicy.addsExposure(operation)
                ? lookupNotice(clientLegalEntityCode, onboardedInstitutionCode, currency, noticePeriod)
                : GrantResolution.NOT_REQUIRED;
    }

    /** The active grant's verdict on the tenor, whatever the operation. */
    GrantResolution lookupTenor(
            LegalEntityCode clientLegalEntityCode, String onboardedInstitutionCode, String currency, Tenor tenor);

    /** The active grant's verdict on the notice period, whatever the operation. */
    GrantResolution lookupNotice(
            LegalEntityCode clientLegalEntityCode,
            String onboardedInstitutionCode,
            String currency,
            NoticePeriod noticePeriod);
}
