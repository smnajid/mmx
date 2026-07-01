package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.mapper.ManagedCurrencyPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataManagedCurrencyRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;

import java.util.List;
import java.util.Optional;

public class JpaManagedCurrencyRepository implements ManagedCurrencyRepository {

    private final SpringDataManagedCurrencyRepository springDataRepository;
    private final ManagedCurrencyPersistenceMapper mapper;
    private final ScopeContextProvider scopeContextProvider;

    public JpaManagedCurrencyRepository(
            SpringDataManagedCurrencyRepository springDataRepository,
            ManagedCurrencyPersistenceMapper mapper,
            ScopeContextProvider scopeContextProvider) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
        this.scopeContextProvider = scopeContextProvider;
    }

    @Override
    public List<ManagedCurrency> findAll() {
        return springDataRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ManagedCurrency> findAllByLegalEntityCode(LegalEntityCode legalEntityCode) {
        return springDataRepository.findByLegalEntityCodeOrderByCodeAsc(legalEntityCode.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ManagedCurrency> findByCode(String code) {
        return springDataRepository.findById(code).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return springDataRepository.existsById(code);
    }

    @Override
    public ManagedCurrency save(ManagedCurrency currency) {
        String hubCode = scopeContextProvider.requireActiveScope().legalEntityCode().value();
        return mapper.toDomain(
                springDataRepository.save(mapper.toEntity(currency, hubCode)));
    }
}
