package com.mmx.order.domain.model;

import java.util.Objects;

/** Permanent link from a TradingClient's onboarded institution to the hub-native institution it was granted from. */
public record HubInstitutionLink(LegalEntityCode hubLegalEntityCode, String hubInstitutionCode) {

    public HubInstitutionLink {
        Objects.requireNonNull(hubLegalEntityCode, "hubLegalEntityCode");
        hubInstitutionCode = Institution.validateCode(hubInstitutionCode);
    }
}
