package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.OnCallInvalidSegmentStatusException;
import com.mmx.order.domain.exception.OnCallSegmentCanceledException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class OnCallRateSegment {

    public static final LocalDate NO_END_DATE = LocalDate.of(2999, 12, 31);

    private final UUID segmentId;
    private final OnCallCurveKey curveKey;
    private final BigDecimal rate;
    private final LocalDate valueDate;
    private final LocalDate endDate;
    private final OnCallRateSegmentStatus status;
    private final Instant validatedAt;

    public OnCallRateSegment(
            UUID segmentId,
            OnCallCurveKey curveKey,
            BigDecimal rate,
            LocalDate valueDate,
            LocalDate endDate,
            OnCallRateSegmentStatus status,
            Instant validatedAt) {
        this.segmentId = Objects.requireNonNull(segmentId, "segmentId must not be null");
        this.curveKey = Objects.requireNonNull(curveKey, "curveKey must not be null");
        this.rate = Objects.requireNonNull(rate, "rate must not be null");
        this.valueDate = Objects.requireNonNull(valueDate, "valueDate must not be null");
        this.endDate = Objects.requireNonNull(endDate, "endDate must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.validatedAt = validatedAt;
    }

    public static OnCallRateSegment createPending(
            UUID segmentId, OnCallCurveKey curveKey, BigDecimal rate, LocalDate valueDate) {
        return new OnCallRateSegment(
                segmentId,
                curveKey,
                rate,
                valueDate,
                NO_END_DATE,
                OnCallRateSegmentStatus.PENDING_CONFIRMATION,
                null);
    }

    public OnCallRateSegment withEndDate(LocalDate newEndDate) {
        return new OnCallRateSegment(
                segmentId, curveKey, rate, valueDate, newEndDate, status, validatedAt);
    }

    public OnCallRateSegment cancel() {
        if (status != OnCallRateSegmentStatus.PENDING_CONFIRMATION) {
            throw new OnCallInvalidSegmentStatusException(status, "cancel");
        }
        return new OnCallRateSegment(
                segmentId, curveKey, rate, valueDate, endDate, OnCallRateSegmentStatus.CANCELED, validatedAt);
    }

    public OnCallRateSegment confirm(Instant validatedAt) {
        Objects.requireNonNull(validatedAt, "validatedAt must not be null");
        if (status == OnCallRateSegmentStatus.VALID) {
            return this;
        }
        if (status == OnCallRateSegmentStatus.CANCELED) {
            throw new OnCallSegmentCanceledException(segmentId);
        }
        if (status != OnCallRateSegmentStatus.PENDING_CONFIRMATION) {
            throw new OnCallInvalidSegmentStatusException(status, "confirm");
        }
        return new OnCallRateSegment(
                segmentId,
                curveKey,
                rate,
                valueDate,
                endDate,
                OnCallRateSegmentStatus.VALID,
                validatedAt);
    }

    public UUID getSegmentId() {
        return segmentId;
    }

    public OnCallCurveKey getCurveKey() {
        return curveKey;
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

    public OnCallRateSegmentStatus getStatus() {
        return status;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }
}
