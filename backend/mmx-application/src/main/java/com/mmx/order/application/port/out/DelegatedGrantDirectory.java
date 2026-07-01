package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;

/**
 * Out-port that resolves a delegated institution grant for a TradingClient intake validation. Given a
 * {@code (clientLegalEntityCode, proxyInstitutionCode, currency)} and a proposed term, reports whether
 * an active grant exists and whether the term is within its enabled set.
 *
 * <p>The port is the single seam consumed by TradingClient intake validation and order routing
 * (Change B). V1 is backed by the {@code delegated_institution_grant} reference data; the adapter maps
 * the client's proxy {@code institutionCode} to the hub native institution referenced by the grant.
 */
public interface DelegatedGrantDirectory {

    GrantResolution resolveTenor(
            LegalEntityCode clientLegalEntityCode,
            String proxyInstitutionCode,
            String currency,
            Tenor tenor);

    GrantResolution resolveNotice(
            LegalEntityCode clientLegalEntityCode,
            String proxyInstitutionCode,
            String currency,
            NoticePeriod noticePeriod);
}
