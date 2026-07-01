package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.entity.DelegatedInstitutionGrantEntity;
import com.mmx.order.adapter.out.persistence.entity.DelegatedInstitutionGrantId;
import com.mmx.order.adapter.out.persistence.mapper.DelegatedGrantPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataDelegatedGrantRepository;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.LegalEntityCode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JpaDelegatedGrantRepository implements DelegatedGrantRepository {

    private final SpringDataDelegatedGrantRepository springDataRepository;
    private final DelegatedGrantPersistenceMapper mapper;

    public JpaDelegatedGrantRepository(
            SpringDataDelegatedGrantRepository springDataRepository, DelegatedGrantPersistenceMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public List<DelegatedInstitutionGrant> findAll() {
        return springDataRepository.findAllByOrderById_HubInstitutionCodeAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<DelegatedInstitutionGrant> findByClientLegalEntityCode(LegalEntityCode clientLegalEntityCode) {
        return springDataRepository
                .findById_ClientLegalEntityCodeOrderById_HubInstitutionCodeAsc(clientLegalEntityCode.value())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<DelegatedInstitutionGrant> findByKey(DelegatedGrantKey key) {
        return springDataRepository.findById(mapper.toId(key)).map(mapper::toDomain);
    }

    @Override
    public boolean existsByKey(DelegatedGrantKey key) {
        return springDataRepository.existsById(mapper.toId(key));
    }

    @Override
    public boolean existsActiveGrantForHubInstitutionAndClient(
            String hubInstitutionCode, LegalEntityCode clientLegalEntityCode) {
        return springDataRepository.existsActiveGrantForHubInstitutionAndClient(
                hubInstitutionCode, clientLegalEntityCode.value());
    }

    @Override
    public DelegatedInstitutionGrant save(DelegatedInstitutionGrant grant) {
        Instant now = Instant.now();
        DelegatedInstitutionGrantId id = mapper.toId(grant.key());
        Optional<DelegatedInstitutionGrantEntity> existing = springDataRepository.findById(id);
        DelegatedInstitutionGrantEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            mapper.updateEntity(entity, grant, now);
        } else {
            entity = mapper.toEntity(grant, now);
        }
        return mapper.toDomain(springDataRepository.save(entity));
    }
}
