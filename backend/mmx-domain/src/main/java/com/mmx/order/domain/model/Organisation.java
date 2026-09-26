package com.mmx.order.domain.model;

import java.util.Objects;

public final class Organisation {

    private final OrganisationCode code;

    public Organisation(OrganisationCode code) {
        this.code = Objects.requireNonNull(code);
    }

    public OrganisationCode getCode() {
        return code;
    }

    public LegalEntity createTradingHub(LegalEntityCode entityCode, LegalEntityRegistry registry) {
        LegalEntity entity = LegalEntity.tradingHub(entityCode, code);
        return registry.register(entity);
    }

    public LegalEntity createTradingClient(
            LegalEntityCode entityCode, LegalEntity connectedHub, LegalEntityRegistry registry) {
        LegalEntity entity = LegalEntity.tradingClient(entityCode, code, connectedHub);
        return registry.register(entity);
    }
}
