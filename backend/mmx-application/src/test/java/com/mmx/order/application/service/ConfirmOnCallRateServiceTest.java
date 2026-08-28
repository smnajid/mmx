package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ConfirmOnCallRateUseCase;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.domain.exception.OnCallSegmentCanceledException;
import com.mmx.order.domain.exception.OnCallSegmentNotFoundException;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OnCallRateSegmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@Tag("fast")

@ExtendWith(MockitoExtension.class)
class ConfirmOnCallRateServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-31T12:00:00Z");
    private static final OnCallCurveKey CURVE =
            new OnCallCurveKey("HSBC-01", "EUR", NoticePeriod._24H);

    @Mock
    OnCallRateRepository onCallRateRepository;

    @Mock
    Clock clock;

    @InjectMocks
    ConfirmOnCallRateService subject;

    @BeforeEach
    void fixedClock() {
        when(clock.now()).thenReturn(NOW);
    }

    @Test
    void confirm_atomicCas_pendingBecomesValid() {
        UUID id = UUID.randomUUID();
        when(onCallRateRepository.compareAndConfirmPending(id, NOW)).thenReturn(true);

        assertThat(subject.confirm(id)).isEqualTo(ConfirmOnCallRateUseCase.Outcome.CONFIRMED);
    }

    @Test
    void confirm_alreadyValid_idempotentNoOp() {
        UUID id = UUID.randomUUID();
        OnCallRateSegment valid =
                new OnCallRateSegment(
                        id,
                        CURVE,
                        new BigDecimal("3.00"),
                        LocalDate.of(2026, 6, 1),
                        OnCallRateSegment.NO_END_DATE,
                        OnCallRateSegmentStatus.VALID,
                        NOW);
        when(onCallRateRepository.compareAndConfirmPending(id, NOW)).thenReturn(false);
        when(onCallRateRepository.findById(id)).thenReturn(Optional.of(valid));

        assertThat(subject.confirm(id)).isEqualTo(ConfirmOnCallRateUseCase.Outcome.ALREADY_VALID);
        verify(onCallRateRepository, never()).save(any());
    }

    @Test
    void confirm_canceled_throwsConflict() {
        UUID id = UUID.randomUUID();
        OnCallRateSegment canceled =
                OnCallRateSegment.createPending(
                                id, CURVE, new BigDecimal("3.00"), LocalDate.of(2026, 6, 1))
                        .cancel();
        when(onCallRateRepository.compareAndConfirmPending(id, NOW)).thenReturn(false);
        when(onCallRateRepository.findById(id)).thenReturn(Optional.of(canceled));

        assertThatThrownBy(() -> subject.confirm(id)).isInstanceOf(OnCallSegmentCanceledException.class);
    }

    @Test
    void confirm_unknown_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(onCallRateRepository.compareAndConfirmPending(id, NOW)).thenReturn(false);
        when(onCallRateRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.confirm(id)).isInstanceOf(OnCallSegmentNotFoundException.class);
    }
}
