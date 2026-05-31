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
}
