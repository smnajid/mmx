package com.mmx.order.application.support;

import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.OrganisationCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link LegalEntityRepository} for application tests. */
public final class InMemoryLegalEntityRepository implements LegalEntityRepository {

    private final Map<LegalEntityCode, LegalEntity> store = new LinkedHashMap<>();

    public InMemoryLegalEntityRepository put(LegalEntity entity) {
        store.put(entity.getCode(), entity);
        return this;
    }

    @Override
    public Optional<LegalEntity> findByCode(LegalEntityCode code) {
        return Optional.ofNullable(store.get(code));
    }

    @Override
    public List<LegalEntity> findByOrganisationCode(OrganisationCode organisationCode) {
        return new ArrayList<>(store.values());
    }

    @Override
    public boolean belongsToOrganisation(LegalEntityCode code, OrganisationCode organisationCode) {
        return true;
    }
}
