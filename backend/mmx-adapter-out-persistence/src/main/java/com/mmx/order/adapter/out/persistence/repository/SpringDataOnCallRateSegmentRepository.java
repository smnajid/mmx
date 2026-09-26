package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.OnCallRateSegmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataOnCallRateSegmentRepository
        extends JpaRepository<OnCallRateSegmentEntity, UUID> {

    List<OnCallRateSegmentEntity> findByInstitutionCodeOrderByValueDateDesc(String institutionCode);

    Optional<OnCallRateSegmentEntity> findByInstitutionCodeAndCurrencyAndNoticePeriodAndEndDate(
            String institutionCode, String currency, String noticePeriod, LocalDate endDate);

    Optional<OnCallRateSegmentEntity>
            findByInstitutionCodeAndCurrencyAndNoticePeriodAndEndDateAndStatus(
                    String institutionCode,
                    String currency,
                    String noticePeriod,
                    LocalDate endDate,
                    String status);

    Optional<OnCallRateSegmentEntity> findByInstitutionCodeAndCurrencyAndNoticePeriodAndStatus(
            String institutionCode, String currency, String noticePeriod, String status);

    Optional<OnCallRateSegmentEntity>
            findByInstitutionCodeAndCurrencyAndNoticePeriodAndEndDateAndStatusNot(
                    String institutionCode,
                    String currency,
                    String noticePeriod,
                    LocalDate endDate,
                    String excludedStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            UPDATE OnCallRateSegmentEntity e
            SET e.status = 'VALID', e.validatedAt = :validatedAt
            WHERE e.segmentId = :segmentId AND e.status = 'PENDING_CONFIRMATION'
            """)
    int confirmPending(
            @Param("segmentId") UUID segmentId, @Param("validatedAt") Instant validatedAt);

    @Query(
            """
            SELECT s FROM OnCallRateSegmentEntity s
            INNER JOIN InstitutionEntity i ON i.institutionCode = s.institutionCode AND i.active = true
            WHERE s.currency = :currency AND s.noticePeriod = :noticePeriod
            AND s.status IN ('VALID', 'PENDING_CONFIRMATION')
            AND s.endDate = :openEndDate
            ORDER BY s.institutionCode
            """)
    List<OnCallRateSegmentEntity> findOpenSegmentsByCurrencyAndNoticePeriod(
            @Param("currency") String currency,
            @Param("noticePeriod") String noticePeriod,
            @Param("openEndDate") LocalDate openEndDate);

    @Query(
            """
            SELECT s FROM OnCallRateSegmentEntity s
            INNER JOIN InstitutionEntity i ON i.institutionCode = s.institutionCode AND i.active = true
            WHERE s.currency = :currency AND s.noticePeriod = :noticePeriod
            AND s.status IN ('VALID', 'PENDING_CONFIRMATION')
            AND s.valueDate <= :valueDate AND s.endDate >= :valueDate
            ORDER BY s.institutionCode
            """)
    List<OnCallRateSegmentEntity> findSegmentsCoveringDate(
            @Param("currency") String currency,
            @Param("noticePeriod") String noticePeriod,
            @Param("valueDate") LocalDate valueDate);

    @Query(
            """
            SELECT DISTINCT s.currency FROM OnCallRateSegmentEntity s
            INNER JOIN InstitutionEntity i ON i.institutionCode = s.institutionCode AND i.active = true
            WHERE s.status IN ('VALID', 'PENDING_CONFIRMATION')
            AND s.endDate = :openEndDate
            ORDER BY s.currency
            """)
    List<String> findDistinctCurrenciesWithOpenOnCallSegments(@Param("openEndDate") LocalDate openEndDate);
}
