package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.DelegatedInstitutionGrantEntity;
import com.mmx.order.adapter.out.persistence.entity.DelegatedInstitutionGrantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataDelegatedGrantRepository
        extends JpaRepository<DelegatedInstitutionGrantEntity, DelegatedInstitutionGrantId> {

    List<DelegatedInstitutionGrantEntity> findAllByOrderById_HubInstitutionCodeAsc();

    List<DelegatedInstitutionGrantEntity> findById_ClientLegalEntityCodeOrderById_HubInstitutionCodeAsc(
            String clientLegalEntityCode);

    @Query(
            """
            SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END
            FROM DelegatedInstitutionGrantEntity g
            WHERE g.id.hubInstitutionCode = :hubInstitutionCode
              AND g.id.clientLegalEntityCode = :clientLegalEntityCode
              AND g.active = true
            """)
    boolean existsActiveGrantForHubInstitutionAndClient(
            @Param("hubInstitutionCode") String hubInstitutionCode,
            @Param("clientLegalEntityCode") String clientLegalEntityCode);

    @Query(
            """
            SELECT g FROM DelegatedInstitutionGrantEntity g
            WHERE g.id.clientLegalEntityCode = :clientCode
              AND g.id.currency = :currency
              AND g.active = true
              AND g.id.hubInstitutionCode = (
                  SELECT i.hubInstitutionCode FROM InstitutionEntity i
                  WHERE i.institutionCode = :proxyInstitutionCode
                    AND i.hubInstitutionCode IS NOT NULL
              )
            """)
    List<DelegatedInstitutionGrantEntity> findActiveGrantForProxy(
            @Param("clientCode") String clientLegalEntityCode,
            @Param("proxyInstitutionCode") String proxyInstitutionCode,
            @Param("currency") String currency);
}
