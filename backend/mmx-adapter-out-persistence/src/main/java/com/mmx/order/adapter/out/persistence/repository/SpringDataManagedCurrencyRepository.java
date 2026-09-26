package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.ManagedCurrencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataManagedCurrencyRepository extends JpaRepository<ManagedCurrencyEntity, String> {

    List<ManagedCurrencyEntity> findByLegalEntityCodeOrderByCodeAsc(String legalEntityCode);
}
