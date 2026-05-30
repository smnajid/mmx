package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.entity.InstitutionEntity;
import com.mmx.order.adapter.out.persistence.mapper.InstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.domain.model.Institution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JpaInstitutionRepository implements InstitutionRepository {

    private final SpringDataInstitutionRepository springDataRepository;
    private final InstitutionPersistenceMapper mapper;

    public JpaInstitutionRepository(
            SpringDataInstitutionRepository springDataRepository, InstitutionPersistenceMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public List<Institution> findAll() {
        return springDataRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Institution> findActive() {
        return springDataRepository.findByActiveTrueOrderByInstitutionCodeAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Institution> findByInstitutionCode(String institutionCode) {
        return springDataRepository.findById(institutionCode).map(mapper::toDomain);
    }

    @Override
    public boolean existsAny() {
        return springDataRepository.count() > 0;
    }

    @Override
    public int maxSuffixForAcronym(String acronymBase) {
        return springDataRepository.findMaxSuffixForAcronym(acronymBase);
    }

    @Override
    public Institution save(Institution institution) {
        Instant now = Instant.now();
        Optional<InstitutionEntity> existing = springDataRepository.findById(institution.getInstitutionCode());
        InstitutionEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            mapper.updateEntity(entity, institution, now);
        } else {
            entity = mapper.toEntity(institution, now);
        }
        return mapper.toDomain(springDataRepository.save(entity));
    }
}
