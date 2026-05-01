package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataAuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    List<AuditLogEntity> findByOrderIdOrderByEventTimeAsc(UUID orderId);
}
