package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.OnCallInvalidSegmentStatusException;
import com.mmx.order.domain.exception.OnCallSegmentCanceledException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnCallRateTransitionTest {

  private static final Instant T1 = Instant.parse("2026-05-31T12:00:00Z");
  private static final OnCallCurveKey CURVE =
      new OnCallCurveKey("HSBC-01", "EUR", NoticePeriod._24H);

  @Test
  void cancel_fromPending_setsCanceled() {
    OnCallRateSegment pending = pendingSegment();

    OnCallRateSegment canceled = pending.cancel();

    assertThat(canceled.getStatus()).isEqualTo(OnCallRateSegmentStatus.CANCELED);
    assertThat(pending.getStatus()).isEqualTo(OnCallRateSegmentStatus.PENDING_CONFIRMATION);
  }

  @Test
  void cancel_fromValid_throws() {
    OnCallRateSegment valid = validSegment();

    assertThatThrownBy(valid::cancel).isInstanceOf(OnCallInvalidSegmentStatusException.class);
  }

  @Test
  void confirm_fromPending_setsValidAndValidatedAt() {
    OnCallRateSegment pending = pendingSegment();

    OnCallRateSegment confirmed = pending.confirm(T1);

    assertThat(confirmed.getStatus()).isEqualTo(OnCallRateSegmentStatus.VALID);
    assertThat(confirmed.getValidatedAt()).isEqualTo(T1);
  }

  @Test
  void confirm_fromValid_isIdempotentNoOp() {
    OnCallRateSegment valid = validSegment();

    OnCallRateSegment again = valid.confirm(Instant.parse("2026-06-01T00:00:00Z"));

    assertThat(again).isSameAs(valid);
    assertThat(again.getValidatedAt()).isEqualTo(valid.getValidatedAt());
  }

  @Test
  void confirm_fromCanceled_throws() {
    OnCallRateSegment canceled = pendingSegment().cancel();

    assertThatThrownBy(() -> canceled.confirm(T1)).isInstanceOf(OnCallSegmentCanceledException.class);
  }

  private static OnCallRateSegment pendingSegment() {
    return OnCallRateSegment.createPending(
        UUID.randomUUID(), CURVE, new BigDecimal("3.25"), LocalDate.of(2026, 6, 1));
  }

  private static OnCallRateSegment validSegment() {
    return OnCallRateSegment.createPending(
            UUID.randomUUID(), CURVE, new BigDecimal("3.25"), LocalDate.of(2026, 5, 1))
        .confirm(Instant.parse("2026-05-01T10:00:00Z"));
  }
}
