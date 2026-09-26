package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.OnCallBackdatedValueDateException;
import com.mmx.order.domain.exception.OnCallPendingExistsException;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OnCallRateSegmentStatus;

import java.time.LocalDate;
import java.util.Optional;

public final class OnCallRateCurvePolicy {

    public void assertValueDateNotBackdated(LocalDate valueDate, LocalDate today) {
        if (valueDate.isBefore(today)) {
            throw new OnCallBackdatedValueDateException(valueDate, today);
        }
    }

    public void assertNoPendingOnCurve(Optional<OnCallRateSegment> pendingOnCurve) {
        if (pendingOnCurve
                .filter(s -> s.getStatus() == OnCallRateSegmentStatus.PENDING_CONFIRMATION)
                .isPresent()) {
            throw new OnCallPendingExistsException(
                    pendingOnCurve.orElseThrow().getCurveKey());
        }
    }

    public LocalDate computeSupersededPriorEndDate(LocalDate newValueDate) {
        return newValueDate.minusDays(1);
    }
}
