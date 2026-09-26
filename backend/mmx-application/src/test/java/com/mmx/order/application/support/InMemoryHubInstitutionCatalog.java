package com.mmx.order.application.support;

import com.mmx.order.application.port.out.HubInstitutionCatalog;
import com.mmx.order.domain.model.Institution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The connected hub's native institutions, as seen by a TradingClient, for application tests. */
public final class InMemoryHubInstitutionCatalog implements HubInstitutionCatalog {

    private final Map<String, Institution> store = new LinkedHashMap<>();

    public InMemoryHubInstitutionCatalog put(Institution institution) {
        store.put(institution.getInstitutionCode(), institution);
        return this;
    }

    @Override
    public List<Institution> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<Institution> findByInstitutionCode(String hubInstitutionCode) {
        return Optional.ofNullable(store.get(hubInstitutionCode));
    }
}
