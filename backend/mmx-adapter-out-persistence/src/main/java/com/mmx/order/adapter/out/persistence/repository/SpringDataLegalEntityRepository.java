package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.LegalEntityEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataLegalEntityRepository extends JpaRepository<LegalEntityEntity, String> {

    List<LegalEntityEntity> findByOrganisationCodeOrderByCodeAsc(String organisationCode);
}
