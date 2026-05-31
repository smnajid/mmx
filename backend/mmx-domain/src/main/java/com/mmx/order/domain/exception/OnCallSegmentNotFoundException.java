package com.mmx.order.domain.exception;

import java.util.UUID;

public class OnCallSegmentNotFoundException extends RuntimeException {

    public OnCallSegmentNotFoundException(UUID segmentId) {
        super("OnCall rate segment not found: " + segmentId);
    }
}
