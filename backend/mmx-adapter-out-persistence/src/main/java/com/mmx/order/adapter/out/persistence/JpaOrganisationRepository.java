package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.mapper.TenancyPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrganisationRepository;
import com.mmx.order.application.port.out.OrganisationRepository;
import com.mmx.order.domain.model.Organisation;
import com.mmx.order.domain.model.OrganisationCode;
import java.util.Optional;

public class JpaOrganisationRepository implements OrganisationRepository {

    private final SpringDataOrganisationRepository springDataRepository;
    private final TenancyPersistenceMapper mapper;

    public JpaOrganisationRepository(
            SpringDataOrganisationRepository springDataRepository, TenancyPersistenceMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Organisation> findByCode(OrganisationCode code) {
        return springDataRepository.findById(code.value()).map(mapper::toDomain);
    }
}
