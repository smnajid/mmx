package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.OnCallRateSegment;

import java.util.UUID;

public interface OnCallRateHandoffOutbox {

    void scheduleUpdated(OnCallRateSegment segment);

    void scheduleCanceled(UUID segmentId);
}
