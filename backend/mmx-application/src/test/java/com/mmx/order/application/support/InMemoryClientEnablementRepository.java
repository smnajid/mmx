package com.mmx.order.application.support;

import com.mmx.order.application.port.out.ClientEnablementRepository;
import com.mmx.order.domain.model.ClientEnablement;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory {@link ClientEnablementRepository} for application tests. */
public final class InMemoryClientEnablementRepository implements ClientEnablementRepository {

    private final Map<String, ClientEnablement> store = new LinkedHashMap<>();

    private static String key(String institutionCode, String currency) {
        return institutionCode + "/" + currency;
    }

    @Override
    public ClientEnablement find(String institutionCode, String currency) {
        return store.getOrDefault(key(institutionCode, currency), ClientEnablement.empty(institutionCode, currency));
    }

    @Override
    public List<ClientEnablement> findByInstitutionCode(String institutionCode) {
        return store.values().stream().filter(e -> e.institutionCode().equals(institutionCode)).toList();
    }

    @Override
    public void save(LegalEntityCode owningLegalEntityCode, ClientEnablement enablement) {
        store.put(key(enablement.institutionCode(), enablement.currency()), enablement);
    }
}
