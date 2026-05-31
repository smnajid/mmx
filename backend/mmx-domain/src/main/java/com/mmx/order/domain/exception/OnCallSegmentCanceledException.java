package com.mmx.order.domain.exception;

import java.util.UUID;

public class OnCallSegmentCanceledException extends RuntimeException {

    public OnCallSegmentCanceledException(UUID segmentId) {
        super("OnCall rate segment was canceled: " + segmentId);
    }
}
