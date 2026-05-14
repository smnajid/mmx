package com.mmx.order.adapter.out.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "actor_id", nullable = false, length = 100)
    private String actorId;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private String details;

    protected AuditLogEntity() {}

    public AuditLogEntity(UUID orderId, String eventType, String actorId, Instant eventTime) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.actorId = actorId;
        this.eventTime = eventTime;
    }

    public Long getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getEventType() { return eventType; }
    public String getActorId() { return actorId; }
    public Instant getEventTime() { return eventTime; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
