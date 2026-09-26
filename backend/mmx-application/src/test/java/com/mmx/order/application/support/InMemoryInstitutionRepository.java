package com.mmx.order.application.support;

import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link InstitutionRepository} for application tests; keyed by institutionCode. */
public final class InMemoryInstitutionRepository implements InstitutionRepository {

    private final Map<String, Institution> store = new LinkedHashMap<>();
    private final List<Institution> saved = new ArrayList<>();

    public InMemoryInstitutionRepository put(Institution institution) {
        store.put(institution.getInstitutionCode(), institution);
        return this;
    }

    /** Every institution passed to {@link #save}, in order. */
    public List<Institution> saved() {
        return saved;
    }

    @Override
    public List<Institution> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Institution> findNativeByLegalEntityCode(LegalEntityCode legalEntityCode) {
        return store.values().stream()
                .filter(i -> !i.isOnboarded())
                .filter(i -> i.getOwningLegalEntityCode() == null || legalEntityCode.equals(i.getOwningLegalEntityCode()))
                .toList();
    }

    @Override
    public List<Institution> findActive() {
        return store.values().stream().filter(Institution::isActive).toList();
    }

    @Override
    public Optional<Institution> findByInstitutionCode(String institutionCode) {
        return Optional.ofNullable(store.get(institutionCode));
    }

    @Override
    public boolean existsAny() {
        return !store.isEmpty();
    }

    @Override
    public int maxSuffixForAcronym(String acronymBase) {
        return store.keySet().stream()
                .filter(code -> code.startsWith(acronymBase + "-"))
                .mapToInt(code -> Integer.parseInt(code.substring(acronymBase.length() + 1)))
                .max()
                .orElse(0);
    }

    @Override
    public Institution save(Institution institution) {
        store.put(institution.getInstitutionCode(), institution);
        saved.add(institution);
        return institution;
    }
}
