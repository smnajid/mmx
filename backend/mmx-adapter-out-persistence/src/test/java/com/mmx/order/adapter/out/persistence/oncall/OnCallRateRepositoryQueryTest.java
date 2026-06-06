package com.mmx.order.adapter.out.persistence.oncall;

import com.mmx.order.adapter.out.persistence.JpaOnCallRateRepository;
import com.mmx.order.adapter.out.persistence.entity.InstitutionEntity;
import com.mmx.order.adapter.out.persistence.mapper.InstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.mapper.OnCallRateSegmentPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOnCallRateSegmentRepository;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OnCallRateSegmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OnCallRateRepositoryQueryTest {

    private static final OnCallCurveKey EUR_24H = new OnCallCurveKey("BNKCO", "EUR", NoticePeriod._24H);
    private static final OnCallCurveKey EUR_24H_CDNRD = new OnCallCurveKey("CDNRD", "EUR", NoticePeriod._24H);

    @Autowired
    SpringDataOnCallRateSegmentRepository springDataRepository;

    @Autowired
    SpringDataInstitutionRepository springDataInstitutionRepository;

    @Autowired
    InstitutionPersistenceMapper institutionMapper;

    JpaOnCallRateRepository repository;

    @BeforeEach
    void setUp() {
        repository =
                new JpaOnCallRateRepository(
                        springDataRepository, new OnCallRateSegmentPersistenceMapper());
        springDataRepository.deleteAll();
        springDataInstitutionRepository.deleteAll();
        seedInstitution("BNKCO", "BankCo", true);
        seedInstitution("CDNRD", "Canada Rd", true);
        seedInstitution("DEAD-01", "Dead Bank", false);
    }

    @Test
    void findOpenSegmentsByCurrencyAndNoticePeriod_returnsValidAndPendingConfirmationOpenSegments() {
        repository.save(
                openValidSegment(
                        UUID.randomUUID(), EUR_24H, "2.85000000", LocalDate.of(2026, 6, 1)));
        repository.save(
                OnCallRateSegment.createPending(
                        UUID.randomUUID(),
                        EUR_24H_CDNRD,
                        new BigDecimal("2.90000000"),
                        LocalDate.of(2026, 6, 10)));
        repository.save(
                closedValidSegment(
                        UUID.randomUUID(),
                        new OnCallCurveKey("BNKCO", "USD", NoticePeriod._24H),
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 5, 31)));
        repository.save(
                openValidSegment(
                        UUID.randomUUID(),
                        new OnCallCurveKey("DEAD-01", "EUR", NoticePeriod._24H),
                        "2.50000000",
                        LocalDate.of(2026, 6, 1)));

        var segments =
                repository.findOpenSegmentsByCurrencyAndNoticePeriod("EUR", NoticePeriod._24H);

        assertThat(segments).hasSize(2);
        assertThat(segments)
                .extracting(segment -> segment.getCurveKey().institutionCode())
                .containsExactlyInAnyOrder("BNKCO", "CDNRD");
        assertThat(segments)
                .extracting(OnCallRateSegment::getStatus)
                .containsExactlyInAnyOrder(
                        OnCallRateSegmentStatus.VALID, OnCallRateSegmentStatus.PENDING_CONFIRMATION);
    }

    @Test
    void findSegmentsCoveringDate_returnsSegmentsWhereRequestedDateIsWithinRange() {
        repository.save(
                segment(
                        UUID.randomUUID(),
                        EUR_24H,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        OnCallRateSegmentStatus.VALID,
                        "2.85000000"));
        repository.save(
                segment(
                        UUID.randomUUID(),
                        EUR_24H_CDNRD,
                        LocalDate.of(2026, 7, 1),
                        OnCallRateSegment.NO_END_DATE,
                        OnCallRateSegmentStatus.PENDING_CONFIRMATION,
                        "2.90000000"));

        var covering =
                repository.findSegmentsCoveringDate(
                        "EUR", NoticePeriod._24H, LocalDate.of(2026, 6, 9));

        assertThat(covering).hasSize(1);
        assertThat(covering.getFirst().getCurveKey().institutionCode()).isEqualTo("BNKCO");
        assertThat(covering.getFirst().getRate()).isEqualByComparingTo(new BigDecimal("2.85000000"));
    }

    @Test
    void findDistinctCurrenciesWithOpenOnCallSegments_returnsCurrenciesFromActiveInstitutionsOnly() {
        repository.save(
                openValidSegment(
                        UUID.randomUUID(), EUR_24H, "2.85000000", LocalDate.of(2026, 6, 1)));
        repository.save(
                openValidSegment(
                        UUID.randomUUID(),
                        new OnCallCurveKey("DEAD-01", "USD", NoticePeriod._24H),
                        "2.50000000",
                        LocalDate.of(2026, 6, 1)));

        assertThat(repository.findDistinctCurrenciesWithOpenOnCallSegments()).containsExactly("EUR");
    }

    private OnCallRateSegment openValidSegment(
            UUID id, OnCallCurveKey curve, String rate, LocalDate valueDate) {
        return segment(
                id,
                curve,
                valueDate,
                OnCallRateSegment.NO_END_DATE,
                OnCallRateSegmentStatus.VALID,
                rate);
    }

    private OnCallRateSegment closedValidSegment(
            UUID id, OnCallCurveKey curve, LocalDate valueDate, LocalDate endDate) {
        return segment(id, curve, valueDate, endDate, OnCallRateSegmentStatus.VALID, "2.50000000");
    }

    private OnCallRateSegment segment(
            UUID id,
            OnCallCurveKey curve,
            LocalDate valueDate,
            LocalDate endDate,
            OnCallRateSegmentStatus status,
            String rate) {
        return new OnCallRateSegment(
                id,
                curve,
                new BigDecimal(rate),
                valueDate,
                endDate,
                status,
                status == OnCallRateSegmentStatus.VALID
                        ? Instant.parse("2026-06-01T00:00:00Z")
                        : null);
    }

    private void seedInstitution(String code, String displayName, boolean active) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        InstitutionEntity entity =
                institutionMapper.toEntity(new Institution(code, displayName, active), now);
        springDataInstitutionRepository.save(entity);
    }
}
