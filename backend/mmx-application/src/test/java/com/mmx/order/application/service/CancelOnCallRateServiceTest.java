package com.mmx.order.application.service;

import com.mmx.order.application.command.CancelOnCallRateCommand;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OnCallRateHandoffOutbox;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.domain.exception.OnCallInvalidSegmentStatusException;
import com.mmx.order.domain.exception.OnCallSegmentNotFoundException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OnCallRateSegmentStatus;
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
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelOnCallRateServiceTest {

    private static final OnCallCurveKey CURVE =
            new OnCallCurveKey("HSBC-01", "EUR", NoticePeriod._24H);

    @Mock
    OnCallRateRepository onCallRateRepository;

    @Mock
    InstitutionRepository institutionRepository;

    @Mock
    OnCallRateHandoffOutbox onCallRateHandoffOutbox;

    @InjectMocks
    CancelOnCallRateService subject;

    @Test
    void cancel_pending_revertsPriorAndSchedulesCanceledOutbox() {
        UUID pendingId = UUID.randomUUID();
        OnCallRateSegment pending =
                OnCallRateSegment.createPending(
                        pendingId, CURVE, new BigDecimal("3.50"), LocalDate.of(2026, 6, 1));
        OnCallRateSegment prior =
                new OnCallRateSegment(
                        UUID.randomUUID(),
                        CURVE,
                        new BigDecimal("3.00"),
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 5, 31),
                        OnCallRateSegmentStatus.VALID,
                        Instant.parse("2026-01-02T00:00:00Z"));

        when(institutionRepository.findByInstitutionCode("HSBC-01"))
                .thenReturn(Optional.of(new Institution("HSBC-01", "HSBC", true)));
        when(onCallRateRepository.findById(pendingId)).thenReturn(Optional.of(pending));
        when(onCallRateRepository.findSupersededPrior(pending)).thenReturn(Optional.of(prior));
        when(onCallRateRepository.save(any(OnCallRateSegment.class))).then(returnsFirstArg());

        OnCallRateSegment result =
                subject.cancel(new CancelOnCallRateCommand("HSBC-01", pendingId));

        assertThat(result.getStatus()).isEqualTo(OnCallRateSegmentStatus.CANCELED);
        verify(onCallRateHandoffOutbox).scheduleCanceled(pendingId);
    }

    @Test
    void cancel_valid_throws() {
        UUID id = UUID.randomUUID();
        OnCallRateSegment valid =
                new OnCallRateSegment(
                        id,
                        CURVE,
                        new BigDecimal("3.00"),
                        LocalDate.of(2026, 1, 1),
                        OnCallRateSegment.NO_END_DATE,
                        OnCallRateSegmentStatus.VALID,
                        Instant.parse("2026-01-02T00:00:00Z"));
        when(institutionRepository.findByInstitutionCode("HSBC-01"))
                .thenReturn(Optional.of(new Institution("HSBC-01", "HSBC", true)));
        when(onCallRateRepository.findById(id)).thenReturn(Optional.of(valid));

        assertThatThrownBy(() -> subject.cancel(new CancelOnCallRateCommand("HSBC-01", id)))
                .isInstanceOf(OnCallInvalidSegmentStatusException.class);

        verify(onCallRateHandoffOutbox, never()).scheduleCanceled(any());
    }

    @Test
    void cancel_unknown_throws() {
        UUID id = UUID.randomUUID();
        when(institutionRepository.findByInstitutionCode("HSBC-01"))
                .thenReturn(Optional.of(new Institution("HSBC-01", "HSBC", true)));
        when(onCallRateRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.cancel(new CancelOnCallRateCommand("HSBC-01", id)))
                .isInstanceOf(OnCallSegmentNotFoundException.class);
    }
}
