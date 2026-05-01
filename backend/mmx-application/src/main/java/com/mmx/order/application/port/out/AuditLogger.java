package com.mmx.order.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogger {

    void log(UUID orderId, String eventType, String actorId, Instant eventTime);
}
