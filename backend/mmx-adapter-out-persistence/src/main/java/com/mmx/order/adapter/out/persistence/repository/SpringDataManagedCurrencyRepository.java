package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.ManagedCurrencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataManagedCurrencyRepository extends JpaRepository<ManagedCurrencyEntity, String> {}
