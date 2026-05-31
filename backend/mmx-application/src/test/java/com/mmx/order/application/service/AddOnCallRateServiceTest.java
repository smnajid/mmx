package com.mmx.order.application.service;

import com.mmx.order.application.command.AddOnCallRateCommand;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OnCallRateHandoffOutbox;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.domain.exception.OnCallBackdatedValueDateException;
import com.mmx.order.domain.exception.OnCallPendingExistsException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OnCallRateSegmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
@MockitoSettings(strictness = Strictness.LENIENT)
class AddOnCallRateServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 31);
    private static final Instant NOW = Instant.parse("2026-05-31T10:00:00Z");
    private static final OnCallCurveKey CURVE =
            new OnCallCurveKey("HSBC-01", "EUR", NoticePeriod._24H);

    @Mock
    OnCallRateRepository onCallRateRepository;

    @Mock
    InstitutionRepository institutionRepository;

    @Mock
    OnCallRateHandoffOutbox onCallRateHandoffOutbox;

    @Mock
    ReferenceGenerator referenceGenerator;

    @Mock
    Clock clock;

    @InjectMocks
    AddOnCallRateService subject;

    @BeforeEach
    void fixedClock() {
        when(clock.today()).thenReturn(TODAY);
        when(clock.now()).thenReturn(NOW);
        when(institutionRepository.findByInstitutionCode("HSBC-01"))
                .thenReturn(Optional.of(new Institution("HSBC-01", "HSBC", true)));
        when(onCallRateRepository.findPendingForCurveKey(CURVE)).thenReturn(Optional.empty());
        when(onCallRateRepository.save(any(OnCallRateSegment.class))).then(returnsFirstArg());
    }

    @Test
    void add_firstEverSegment_persistsPendingAndSchedulesOutbox() {
        UUID newId = UUID.randomUUID();
        when(referenceGenerator.generateSegmentId()).thenReturn(newId);
        when(onCallRateRepository.findOpenSegment(CURVE)).thenReturn(Optional.empty());

        AddOnCallRateCommand command =
                new AddOnCallRateCommand(
                        "HSBC-01", "EUR", NoticePeriod._24H, new BigDecimal("3.25"), TODAY);

        OnCallRateSegment result = subject.add(command);

        assertThat(result.getSegmentId()).isEqualTo(newId);
        assertThat(result.getStatus()).isEqualTo(OnCallRateSegmentStatus.PENDING_CONFIRMATION);
        assertThat(result.getEndDate()).isEqualTo(OnCallRateSegment.NO_END_DATE);
        verify(onCallRateHandoffOutbox).scheduleUpdated(result);
    }

    @Test
    void add_supersedesPriorOpenSegmentEndDate() {
        UUID newId = UUID.randomUUID();
        OnCallRateSegment priorValid =
                new OnCallRateSegment(
                        UUID.randomUUID(),
                        CURVE,
                        new BigDecimal("3.00"),
                        LocalDate.of(2026, 1, 1),
                        OnCallRateSegment.NO_END_DATE,
                        OnCallRateSegmentStatus.VALID,
                        Instant.parse("2026-01-02T00:00:00Z"));
        when(referenceGenerator.generateSegmentId()).thenReturn(newId);
        when(onCallRateRepository.findOpenSegment(CURVE)).thenReturn(Optional.of(priorValid));

        LocalDate valueDate = LocalDate.of(2026, 6, 1);
        subject.add(
                new AddOnCallRateCommand(
                        "HSBC-01", "EUR", NoticePeriod._24H, new BigDecimal("3.50"), valueDate));

        ArgumentCaptor<OnCallRateSegment> saved = ArgumentCaptor.forClass(OnCallRateSegment.class);
        verify(onCallRateRepository, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .anyMatch(
                        s ->
                                s.getSegmentId().equals(priorValid.getSegmentId())
                                        && s.getEndDate().equals(LocalDate.of(2026, 5, 31)));
    }

    @Test
    void add_rejectsSecondPending() {
        OnCallRateSegment pending =
                OnCallRateSegment.createPending(
                        UUID.randomUUID(), CURVE, new BigDecimal("3.25"), TODAY);
        when(onCallRateRepository.findPendingForCurveKey(CURVE)).thenReturn(Optional.of(pending));

        assertThatThrownBy(
                        () ->
                                subject.add(
                                        new AddOnCallRateCommand(
                                                "HSBC-01",
                                                "EUR",
                                                NoticePeriod._24H,
                                                new BigDecimal("3.30"),
                                                TODAY.plusDays(1))))
                .isInstanceOf(OnCallPendingExistsException.class);

        verify(onCallRateHandoffOutbox, never()).scheduleUpdated(any());
    }

    @Test
    void add_rejectsBackdatedValueDate() {
        assertThatThrownBy(
                        () ->
                                subject.add(
                                        new AddOnCallRateCommand(
                                                "HSBC-01",
                                                "EUR",
                                                NoticePeriod._24H,
                                                new BigDecimal("3.25"),
                                                TODAY.minusDays(1))))
                .isInstanceOf(OnCallBackdatedValueDateException.class);

        verify(onCallRateRepository, never()).save(any());
    }
}
