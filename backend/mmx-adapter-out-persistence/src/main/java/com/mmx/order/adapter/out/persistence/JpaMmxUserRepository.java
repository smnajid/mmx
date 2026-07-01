package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.mapper.MmxUserPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataMmxUserRepository;
import com.mmx.order.application.port.out.MmxUserRepository;
import com.mmx.order.domain.model.MmxUser;
import com.mmx.order.domain.model.MmxUserId;
import java.util.Optional;

public class JpaMmxUserRepository implements MmxUserRepository {

    private final SpringDataMmxUserRepository springDataRepository;
    private final MmxUserPersistenceMapper mapper;

    public JpaMmxUserRepository(SpringDataMmxUserRepository springDataRepository, MmxUserPersistenceMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<MmxUser> findById(MmxUserId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public MmxUser save(MmxUser user) {
        return mapper.toDomain(springDataRepository.save(mapper.toEntity(user)));
    }
}
