package com.mmx.order.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "oncall_rate_segment")
public class OnCallRateSegmentEntity {

    @Id
    @Column(name = "segment_id", nullable = false, updatable = false)
    private UUID segmentId;

    @Column(name = "institution_code", nullable = false, length = 32)
    private String institutionCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "notice_period", nullable = false, length = 10)
    private String noticePeriod;

    @Column(name = "rate", nullable = false, precision = 12, scale = 8)
    private BigDecimal rate;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "legal_entity_code", nullable = false, length = 3)
    private String legalEntityCode;

    protected OnCallRateSegmentEntity() {}

    public OnCallRateSegmentEntity(
            UUID segmentId,
            String institutionCode,
            String currency,
            String noticePeriod,
            BigDecimal rate,
            LocalDate valueDate,
            LocalDate endDate,
            String status,
            Instant validatedAt,
            String legalEntityCode) {
        this.segmentId = segmentId;
        this.institutionCode = institutionCode;
        this.currency = currency;
        this.noticePeriod = noticePeriod;
        this.rate = rate;
        this.valueDate = valueDate;
        this.endDate = endDate;
        this.status = status;
        this.validatedAt = validatedAt;
        this.legalEntityCode = legalEntityCode;
    }

    public UUID getSegmentId() {
        return segmentId;
    }

    public String getInstitutionCode() {
        return institutionCode;
    }

    public String getCurrency() {
        return currency;
    }

    public String getNoticePeriod() {
        return noticePeriod;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getStatus() {
        return status;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }
}
