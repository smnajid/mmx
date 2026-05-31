package com.mmx.order.domain.exception;

import com.mmx.order.domain.model.OnCallRateSegmentStatus;

public class OnCallInvalidSegmentStatusException extends RuntimeException {

    public OnCallInvalidSegmentStatusException(OnCallRateSegmentStatus status, String operation) {
        super("Cannot " + operation + " OnCall segment in status " + status);
    }
}
