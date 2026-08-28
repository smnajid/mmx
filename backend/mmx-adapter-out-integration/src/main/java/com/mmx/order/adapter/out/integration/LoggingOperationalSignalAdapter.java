package com.mmx.order.adapter.out.integration;

import com.mmx.order.application.port.out.OperationalSignalPort;
import com.mmx.order.application.port.out.RemoteRoutingRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * SLF4J-backed {@link OperationalSignalPort}. Logs the circuit-open signal at WARN level. In
 * production this is the hook for the ops/observability stack (metrics, on-call alert).
 */
public class LoggingOperationalSignalAdapter implements OperationalSignalPort {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingOperationalSignalAdapter.class);

    @Override
    public void emitRemoteRoutingCircuitOpen(
            RemoteRoutingRequest request, int consecutiveFailures, Instant openedAt) {
        LOG.warn(
                "Remote routing circuit OPEN: originatingLE={}, routingId={}, consecutiveFailures={}, openedAt={}",
                request.originatingLegalEntityCode(),
                request.routingId(),
                consecutiveFailures,
                openedAt);
    }
}
