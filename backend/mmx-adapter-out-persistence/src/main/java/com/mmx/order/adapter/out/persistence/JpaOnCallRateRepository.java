package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.mapper.OnCallRateSegmentPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOnCallRateSegmentRepository;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OnCallRateSegmentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JpaOnCallRateRepository implements OnCallRateRepository {

    private final SpringDataOnCallRateSegmentRepository springDataRepository;
    private final OnCallRateSegmentPersistenceMapper mapper;

    public JpaOnCallRateRepository(
            SpringDataOnCallRateSegmentRepository springDataRepository,
            OnCallRateSegmentPersistenceMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<OnCallRateSegment> findById(UUID segmentId) {
        return springDataRepository.findById(segmentId).map(mapper::toDomain);
    }

    @Override
    public List<OnCallRateSegment> findByInstitutionCode(String institutionCode) {
        return springDataRepository.findByInstitutionCodeOrderByValueDateDesc(institutionCode).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<OnCallRateSegment> findOpenSegment(OnCallCurveKey curveKey) {
        return springDataRepository
                .findByInstitutionCodeAndCurrencyAndNoticePeriodAndEndDate(
                        curveKey.institutionCode(),
                        curveKey.currency(),
                        curveKey.noticePeriod().name(),
                        OnCallRateSegment.NO_END_DATE)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<OnCallRateSegment> findPendingForCurveKey(OnCallCurveKey curveKey) {
        return springDataRepository
                .findByInstitutionCodeAndCurrencyAndNoticePeriodAndStatus(
                        curveKey.institutionCode(),
                        curveKey.currency(),
                        curveKey.noticePeriod().name(),
                        OnCallRateSegmentStatus.PENDING_CONFIRMATION.name())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<OnCallRateSegment> findSupersededPrior(OnCallRateSegment pendingSegment) {
        LocalDate priorEnd = pendingSegment.getValueDate().minusDays(1);
        return springDataRepository
                .findByInstitutionCodeAndCurrencyAndNoticePeriodAndEndDateAndStatusNot(
                        pendingSegment.getCurveKey().institutionCode(),
                        pendingSegment.getCurveKey().currency(),
                        pendingSegment.getCurveKey().noticePeriod().name(),
                        priorEnd,
                        OnCallRateSegmentStatus.CANCELED.name())
                .map(mapper::toDomain);
    }

    @Override
    public OnCallRateSegment save(OnCallRateSegment segment) {
        return mapper.toDomain(springDataRepository.save(mapper.toEntity(segment)));
    }

    @Override
    public boolean compareAndConfirmPending(UUID segmentId, Instant validatedAt) {
        return springDataRepository.confirmPending(segmentId, validatedAt) > 0;
    }
}
