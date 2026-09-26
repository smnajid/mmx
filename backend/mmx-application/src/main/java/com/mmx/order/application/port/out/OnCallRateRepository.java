package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;

import com.mmx.order.domain.model.NoticePeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnCallRateRepository {

    Optional<OnCallRateSegment> findById(UUID segmentId);

    List<OnCallRateSegment> findByInstitutionCode(String institutionCode);

    Optional<OnCallRateSegment> findOpenSegment(OnCallCurveKey curveKey);

    Optional<OnCallRateSegment> findPendingForCurveKey(OnCallCurveKey curveKey);

    Optional<OnCallRateSegment> findSupersededPrior(OnCallRateSegment pendingSegment);

    OnCallRateSegment save(OnCallRateSegment segment);

    /**
     * Atomically transitions {@code PENDING_CONFIRMATION} → {@code VALID}.
     *
     * @return {@code true} if a row was updated, {@code false} if no pending row matched
     */
    boolean compareAndConfirmPending(UUID segmentId, Instant validatedAt);

    List<OnCallRateSegment> findOpenSegmentsByCurrencyAndNoticePeriod(
            String currency, NoticePeriod noticePeriod);

    List<OnCallRateSegment> findSegmentsCoveringDate(
            String currency, NoticePeriod noticePeriod, LocalDate valueDate);

    List<String> findDistinctCurrenciesWithOpenOnCallSegments();
}
