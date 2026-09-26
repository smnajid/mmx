package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.mapper.TenancyPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataLegalEntityRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.OrganisationCode;
import java.util.List;
import java.util.Optional;

public class JpaLegalEntityRepository implements LegalEntityRepository {

    private final SpringDataLegalEntityRepository springDataRepository;
    private final TenancyPersistenceMapper mapper;

    public JpaLegalEntityRepository(
            SpringDataLegalEntityRepository springDataRepository, TenancyPersistenceMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<LegalEntity> findByCode(LegalEntityCode code) {
        return springDataRepository.findById(code.value()).map(mapper::toDomain);
    }

    @Override
    public List<LegalEntity> findByOrganisationCode(OrganisationCode organisationCode) {
        return springDataRepository.findByOrganisationCodeOrderByCodeAsc(organisationCode.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean belongsToOrganisation(LegalEntityCode code, OrganisationCode organisationCode) {
        return springDataRepository
                .findById(code.value())
                .map(entity -> organisationCode.value().equals(entity.getOrganisationCode()))
                .orElse(false);
    }
}
