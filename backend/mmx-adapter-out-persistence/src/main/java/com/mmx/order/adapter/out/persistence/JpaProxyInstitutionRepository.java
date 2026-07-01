package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.entity.InstitutionEntity;
import com.mmx.order.adapter.out.persistence.mapper.ProxyInstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.application.port.out.ProxyInstitutionRepository;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ThinProxyInstitution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JpaProxyInstitutionRepository implements ProxyInstitutionRepository {

    private final SpringDataInstitutionRepository springDataRepository;
    private final ProxyInstitutionPersistenceMapper mapper;
    private final ScopeContextProvider scopeContextProvider;

    public JpaProxyInstitutionRepository(
            SpringDataInstitutionRepository springDataRepository,
            ProxyInstitutionPersistenceMapper mapper,
            ScopeContextProvider scopeContextProvider) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
        this.scopeContextProvider = scopeContextProvider;
    }

    @Override
    public List<ThinProxyInstitution> findAll() {
        return springDataRepository.findAll().stream()
                .filter(e -> e.getHubInstitutionCode() != null)
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<ThinProxyInstitution> findByClientLegalEntity(LegalEntityCode clientLegalEntityCode) {
        return springDataRepository
                .findByLegalEntityCodeAndHubInstitutionCodeIsNotNullOrderByInstitutionCodeAsc(
                        clientLegalEntityCode.value())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ThinProxyInstitution> findByInstitutionCode(String institutionCode) {
        return springDataRepository
                .findById(institutionCode)
                .filter(e -> e.getHubInstitutionCode() != null)
                .map(mapper::toDomain);
    }

    @Override
    public int maxSuffixForAcronym(String acronymBase) {
        return springDataRepository.findMaxSuffixForAcronym(acronymBase);
    }

    @Override
    public ThinProxyInstitution save(ThinProxyInstitution proxy) {
        Instant now = Instant.now();
        String clientCode = scopeContextProvider.requireActiveScope().legalEntityCode().value();
        Optional<InstitutionEntity> existing = springDataRepository.findById(proxy.getInstitutionCode());
        InstitutionEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            mapper.updateEntity(entity, proxy, now);
        } else {
            entity = mapper.toEntity(proxy, clientCode, now);
        }
        return mapper.toDomain(springDataRepository.save(entity));
    }
}
