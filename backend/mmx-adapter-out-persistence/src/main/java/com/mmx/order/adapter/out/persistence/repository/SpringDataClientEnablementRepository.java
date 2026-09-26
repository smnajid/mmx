package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.ClientEnablementEntity;
import com.mmx.order.adapter.out.persistence.entity.ClientEnablementId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataClientEnablementRepository extends JpaRepository<ClientEnablementEntity, ClientEnablementId> {

    List<ClientEnablementEntity> findByIdInstitutionCodeOrderByIdCurrencyAsc(String institutionCode);
}
