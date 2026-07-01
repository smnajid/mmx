package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.List;
import java.util.Optional;

public interface ManagedCurrencyRepository {

    List<ManagedCurrency> findAll();

    List<ManagedCurrency> findAllByLegalEntityCode(LegalEntityCode legalEntityCode);

    Optional<ManagedCurrency> findByCode(String code);

    boolean existsByCode(String code);

    ManagedCurrency save(ManagedCurrency currency);
}
