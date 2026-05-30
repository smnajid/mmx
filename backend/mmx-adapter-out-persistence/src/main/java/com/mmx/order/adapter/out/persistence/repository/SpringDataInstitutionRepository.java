package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.InstitutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataInstitutionRepository extends JpaRepository<InstitutionEntity, String> {

    List<InstitutionEntity> findByActiveTrueOrderByInstitutionCodeAsc();

    @Query(
            """
            SELECT COALESCE(MAX(CAST(SUBSTRING(i.institutionCode, LENGTH(:base) + 2) AS int)), 0)
            FROM InstitutionEntity i
            WHERE i.institutionCode LIKE CONCAT(:base, '-%')
            """)
    int findMaxSuffixForAcronym(@Param("base") String acronymBase);
}
