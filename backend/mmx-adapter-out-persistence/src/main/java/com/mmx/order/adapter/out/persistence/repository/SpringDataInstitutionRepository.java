package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.InstitutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpringDataInstitutionRepository extends JpaRepository<InstitutionEntity, String> {

    List<InstitutionEntity> findByActiveTrueOrderByInstitutionCodeAsc();

    List<InstitutionEntity> findByLegalEntityCodeAndHubInstitutionCodeIsNullOrderByInstitutionCodeAsc(
            String legalEntityCode);

    List<InstitutionEntity> findByLegalEntityCodeAndHubInstitutionCodeIsNotNullOrderByInstitutionCodeAsc(
            String legalEntityCode);

    Optional<InstitutionEntity> findByLegalEntityCodeAndHubLegalEntityCodeAndHubInstitutionCode(
            String legalEntityCode, String hubLegalEntityCode, String hubInstitutionCode);

    @Query(
            """
            SELECT COALESCE(MAX(CAST(SUBSTRING(i.institutionCode, LENGTH(:base) + 2) AS int)), 0)
            FROM InstitutionEntity i
            WHERE i.institutionCode LIKE CONCAT(:base, '-%')
            """)
    int findMaxSuffixForAcronym(@Param("base") String acronymBase);
}
