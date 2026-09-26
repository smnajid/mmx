package com.mmx.order.application.service;

import com.mmx.order.application.command.CancelOnCallRateCommand;
import com.mmx.order.application.exception.InstitutionNotFoundException;
import com.mmx.order.application.port.in.CancelOnCallRateUseCase;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OnCallRateHandoffOutbox;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.domain.exception.OnCallSegmentNotFoundException;
import com.mmx.order.domain.model.OnCallRateSegment;

public final class CancelOnCallRateService implements CancelOnCallRateUseCase {

    private final OnCallRateRepository onCallRateRepository;
    private final InstitutionRepository institutionRepository;
    private final OnCallRateHandoffOutbox onCallRateHandoffOutbox;

    public CancelOnCallRateService(
            OnCallRateRepository onCallRateRepository,
            InstitutionRepository institutionRepository,
            OnCallRateHandoffOutbox onCallRateHandoffOutbox) {
        this.onCallRateRepository = onCallRateRepository;
        this.institutionRepository = institutionRepository;
        this.onCallRateHandoffOutbox = onCallRateHandoffOutbox;
    }

    @Override
    public OnCallRateSegment cancel(CancelOnCallRateCommand command) {
        requireInstitution(command.institutionCode());
        OnCallRateSegment segment =
                onCallRateRepository
                        .findById(command.segmentId())
                        .orElseThrow(() -> new OnCallSegmentNotFoundException(command.segmentId()));

        if (!segment.getCurveKey().institutionCode().equals(command.institutionCode())) {
            throw new OnCallSegmentNotFoundException(command.segmentId());
        }

        onCallRateRepository
                .findSupersededPrior(segment)
                .ifPresent(
                        prior ->
                                onCallRateRepository.save(
                                        prior.withEndDate(OnCallRateSegment.NO_END_DATE)));

        OnCallRateSegment canceled = onCallRateRepository.save(segment.cancel());
        onCallRateHandoffOutbox.scheduleCanceled(canceled.getSegmentId());
        return canceled;
    }

    private void requireInstitution(String institutionCode) {
        institutionRepository
                .findByInstitutionCode(institutionCode)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionCode));
    }
}
