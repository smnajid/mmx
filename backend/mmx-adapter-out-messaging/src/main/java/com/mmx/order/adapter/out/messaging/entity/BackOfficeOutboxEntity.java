package com.mmx.order.adapter.out.messaging.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "back_office_outbox")
public class BackOfficeOutboxEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackOfficeOutboxRowStatus status;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    protected BackOfficeOutboxEntity() {}

    public BackOfficeOutboxEntity(
            UUID id,
            UUID orderId,
            String payload,
            BackOfficeOutboxRowStatus status,
            Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.payload = payload;
        this.status = status;
        this.publishAttempts = 0;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getPayload() {
        return payload;
    }

    public BackOfficeOutboxRowStatus getStatus() {
        return status;
    }

    public void setStatus(BackOfficeOutboxRowStatus status) {
        this.status = status;
    }

    public int getPublishAttempts() {
        return publishAttempts;
    }

    public void setPublishAttempts(int publishAttempts) {
        this.publishAttempts = publishAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }
}
