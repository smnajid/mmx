package com.mmx.order.application.support;

import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link DelegatedGrantRepository} for application tests. */
public final class InMemoryDelegatedGrantRepository implements DelegatedGrantRepository {

    private final Map<DelegatedGrantKey, DelegatedInstitutionGrant> store = new LinkedHashMap<>();

    @Override
    public List<DelegatedInstitutionGrant> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<DelegatedInstitutionGrant> findByClientLegalEntityCode(LegalEntityCode clientLegalEntityCode) {
        return store.values().stream()
                .filter(g -> g.getClientLegalEntityCode().equals(clientLegalEntityCode))
                .toList();
    }

    @Override
    public Optional<DelegatedInstitutionGrant> findByKey(DelegatedGrantKey key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public boolean existsByKey(DelegatedGrantKey key) {
        return store.containsKey(key);
    }

    @Override
    public boolean existsActiveGrantForHubInstitutionAndClient(
            String hubInstitutionCode, LegalEntityCode clientLegalEntityCode) {
        return store.values().stream()
                .anyMatch(
                        g -> g.getHubInstitutionCode().equals(hubInstitutionCode)
                                && g.getClientLegalEntityCode().equals(clientLegalEntityCode)
                                && g.isActive());
    }

    @Override
    public DelegatedInstitutionGrant save(DelegatedInstitutionGrant grant) {
        store.put(grant.key(), grant);
        return grant;
    }
}
