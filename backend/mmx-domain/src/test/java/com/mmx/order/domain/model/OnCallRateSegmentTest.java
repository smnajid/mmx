package com.mmx.order.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
@Tag("fast")

class OnCallRateSegmentTest {

  private static final OnCallCurveKey CURVE =
      new OnCallCurveKey("HSBC-01", "EUR", NoticePeriod._24H);

  @Test
  void createPending_openSegmentUsesNoEndSentinel() {
    UUID id = UUID.randomUUID();
    LocalDate valueDate = LocalDate.of(2026, 6, 1);

    OnCallRateSegment segment =
        OnCallRateSegment.createPending(id, CURVE, new BigDecimal("3.25000000"), valueDate);

    assertThat(segment.getSegmentId()).isEqualTo(id);
    assertThat(segment.getCurveKey()).isEqualTo(CURVE);
    assertThat(segment.getRate()).isEqualByComparingTo("3.25000000");
    assertThat(segment.getValueDate()).isEqualTo(valueDate);
    assertThat(segment.getEndDate()).isEqualTo(OnCallRateSegment.NO_END_DATE);
    assertThat(segment.getStatus()).isEqualTo(OnCallRateSegmentStatus.PENDING_CONFIRMATION);
    assertThat(segment.getValidatedAt()).isNull();
  }

  @Test
  void withEndDate_returnsCopyWithNewEnd() {
    OnCallRateSegment pending = samplePending();
    LocalDate newEnd = LocalDate.of(2026, 5, 31);

    OnCallRateSegment shortened = pending.withEndDate(newEnd);

    assertThat(shortened.getEndDate()).isEqualTo(newEnd);
    assertThat(pending.getEndDate()).isEqualTo(OnCallRateSegment.NO_END_DATE);
  }
  
  private static OnCallRateSegment samplePending() {
    return OnCallRateSegment.createPending(
        UUID.randomUUID(), CURVE, new BigDecimal("3.00"), LocalDate.of(2026, 6, 1));
  }
}
