package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ConfirmOnCallRateUseCase;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.domain.exception.OnCallSegmentCanceledException;
import com.mmx.order.domain.exception.OnCallSegmentNotFoundException;
import com.mmx.order.domain.model.OnCallRateSegmentStatus;

import java.util.UUID;

public final class ConfirmOnCallRateService implements ConfirmOnCallRateUseCase {

    private final OnCallRateRepository onCallRateRepository;
    private final Clock clock;

    public ConfirmOnCallRateService(OnCallRateRepository onCallRateRepository, Clock clock) {
        this.onCallRateRepository = onCallRateRepository;
        this.clock = clock;
    }

    @Override
    public Outcome confirm(UUID segmentId) {
        if (onCallRateRepository.compareAndConfirmPending(segmentId, clock.now())) {
            return Outcome.CONFIRMED;
        }
        var segment =
                onCallRateRepository
                        .findById(segmentId)
                        .orElseThrow(() -> new OnCallSegmentNotFoundException(segmentId));
        if (segment.getStatus() == OnCallRateSegmentStatus.VALID) {
            return Outcome.ALREADY_VALID;
        }
        if (segment.getStatus() == OnCallRateSegmentStatus.CANCELED) {
            throw new OnCallSegmentCanceledException(segmentId);
        }
        throw new OnCallSegmentNotFoundException(segmentId);
    }
}
