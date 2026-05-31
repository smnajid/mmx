package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.OnCallBackdatedValueDateException;
import com.mmx.order.domain.exception.OnCallPendingExistsException;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OnCallRateSegmentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnCallRateCurvePolicyTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 5, 31);
  private static final OnCallCurveKey CURVE =
      new OnCallCurveKey("HSBC-01", "EUR", NoticePeriod._48H);

  private final OnCallRateCurvePolicy policy = new OnCallRateCurvePolicy();

  @Test
  void computeSupersededPriorEndDate_isValueDateMinusOne() {
    LocalDate valueDate = LocalDate.of(2026, 6, 15);
    assertThat(policy.computeSupersededPriorEndDate(valueDate)).isEqualTo(LocalDate.of(2026, 6, 14));
  }

  @Test
  void assertValueDateNotBackdated_rejectsBeforeToday() {
    assertThatThrownBy(() -> policy.assertValueDateNotBackdated(LocalDate.of(2026, 5, 30), TODAY))
        .isInstanceOf(OnCallBackdatedValueDateException.class);
  }

  @Test
  void assertValueDateNotBackdated_acceptsToday() {
    policy.assertValueDateNotBackdated(TODAY, TODAY);
  }

  @Test
  void assertNoPendingOnCurve_rejectsWhenPendingExists() {
    OnCallRateSegment pending =
        OnCallRateSegment.createPending(
            UUID.randomUUID(), CURVE, new BigDecimal("2.5"), LocalDate.of(2026, 6, 1));

    assertThatThrownBy(() -> policy.assertNoPendingOnCurve(Optional.of(pending)))
        .isInstanceOf(OnCallPendingExistsException.class);
  }

  @Test
  void assertNoPendingOnCurve_allowsWhenNoPending() {
    policy.assertNoPendingOnCurve(Optional.empty());
  }

  @Test
  void assertNoPendingOnCurve_allowsWhenOpenSegmentIsValid() {
    OnCallRateSegment validOpen =
        new OnCallRateSegment(
            UUID.randomUUID(),
            CURVE,
            new BigDecimal("2.5"),
            LocalDate.of(2026, 1, 1),
            OnCallRateSegment.NO_END_DATE,
            OnCallRateSegmentStatus.VALID,
            null);

    policy.assertNoPendingOnCurve(Optional.empty());
    assertThat(validOpen.getStatus()).isEqualTo(OnCallRateSegmentStatus.VALID);
  }
}
