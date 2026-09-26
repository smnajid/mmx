package com.mmx.order.adapter.out.messaging.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "routing_outcome_outbox")
public class RoutingOutcomeOutboxEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "hub_order_id")
    private UUID hubOrderId;

    @Column(name = "originating_le")
    private String originatingLegalEntityCode;

    @Column(name = "routing_id")
    private UUID routingId;

    @Column(name = "outcome_type")
    private String outcomeType;

    @Column(name = "payload")
    private String payload;

    @Column(name = "status")
    private String status;

    @Column(name = "publish_attempts")
    private int publishAttempts;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    protected RoutingOutcomeOutboxEntity() {}

    public RoutingOutcomeOutboxEntity(
            UUID id,
            UUID hubOrderId,
            String originatingLegalEntityCode,
            UUID routingId,
            String outcomeType,
            String payload,
            String status,
            Instant createdAt) {
        this.id = id;
        this.hubOrderId = hubOrderId;
        this.originatingLegalEntityCode = originatingLegalEntityCode;
        this.routingId = routingId;
        this.outcomeType = outcomeType;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getHubOrderId() { return hubOrderId; }
    public String getOriginatingLegalEntityCode() { return originatingLegalEntityCode; }
    public UUID getRoutingId() { return routingId; }
    public String getOutcomeType() { return outcomeType; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getPublishAttempts() { return publishAttempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }

    public void setStatus(String status) { this.status = status; }
    public void incrementPublishAttempts() { this.publishAttempts++; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
}
