package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.mapper.ManagedCurrencyPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataManagedCurrencyRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.domain.model.ManagedCurrency;

import java.util.List;
import java.util.Optional;

public class JpaManagedCurrencyRepository implements ManagedCurrencyRepository {

    private final SpringDataManagedCurrencyRepository springDataRepository;
    private final ManagedCurrencyPersistenceMapper mapper;

    public JpaManagedCurrencyRepository(
            SpringDataManagedCurrencyRepository springDataRepository, ManagedCurrencyPersistenceMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public List<com.mmx.order.domain.model.ManagedCurrency> findAll() {
        return springDataRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<com.mmx.order.domain.model.ManagedCurrency> findByCode(String code) {
        return springDataRepository.findById(code).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return springDataRepository.existsById(code);
    }

    @Override
    public com.mmx.order.domain.model.ManagedCurrency save(com.mmx.order.domain.model.ManagedCurrency currency) {
        return mapper.toDomain(springDataRepository.save(mapper.toEntity(currency)));
    }
}
