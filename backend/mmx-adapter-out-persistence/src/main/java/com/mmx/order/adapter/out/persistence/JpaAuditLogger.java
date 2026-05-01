package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.entity.AuditLogEntity;
import com.mmx.order.adapter.out.persistence.repository.SpringDataAuditLogRepository;
import com.mmx.order.application.port.out.AuditLogger;

import java.time.Instant;
import java.util.UUID;

public class JpaAuditLogger implements AuditLogger {

    private final SpringDataAuditLogRepository auditLogRepository;

    public JpaAuditLogger(SpringDataAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void log(UUID orderId, String eventType, String actorId, Instant eventTime) {
        auditLogRepository.save(new AuditLogEntity(orderId, eventType, actorId, eventTime));
    }
}
