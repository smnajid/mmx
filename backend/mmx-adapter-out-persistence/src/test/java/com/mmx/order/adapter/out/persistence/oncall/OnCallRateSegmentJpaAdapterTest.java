package com.mmx.order.adapter.out.persistence.oncall;

import com.mmx.order.adapter.out.persistence.JpaOnCallRateRepository;
import com.mmx.order.adapter.out.persistence.PersistenceTestCleanup;
import com.mmx.order.adapter.out.persistence.entity.InstitutionEntity;
import com.mmx.order.adapter.out.persistence.mapper.InstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.mapper.OnCallRateSegmentPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataDelegatedGrantRepository;
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
class OnCallRateSegmentJpaAdapterTest {

    private static final OnCallCurveKey CURVE =
            new OnCallCurveKey("HSBC-01", "EUR", NoticePeriod._24H);

    @Autowired
    SpringDataOnCallRateSegmentRepository springDataRepository;

    @Autowired
    SpringDataInstitutionRepository springDataInstitutionRepository;

    @Autowired
    SpringDataDelegatedGrantRepository grantRepository;

    @Autowired
    InstitutionPersistenceMapper institutionMapper;

    JpaOnCallRateRepository repository;

    @BeforeEach
    void setUp() {
        repository =
                new JpaOnCallRateRepository(
                        springDataRepository, new OnCallRateSegmentPersistenceMapper());
        PersistenceTestCleanup.clearInstitutionsAndOnCall(
                grantRepository, springDataRepository, springDataInstitutionRepository);
        seedInstitution("HSBC-01");
    }

    @Test
    void saveAndFindByInstitution() {
        OnCallRateSegment pending =
                OnCallRateSegment.createPending(
                        UUID.randomUUID(), CURVE, new BigDecimal("3.25"), LocalDate.of(2026, 6, 1));
        repository.save(pending);

        assertThat(repository.findByInstitutionCode("HSBC-01")).hasSize(1);
        assertThat(repository.findById(pending.getSegmentId())).isPresent();
    }

    @Test
    void findOpenSegment_returnsSegmentWithNoEndDate() {
        OnCallRateSegment open =
                new OnCallRateSegment(
                        UUID.randomUUID(),
                        CURVE,
                        new BigDecimal("3.00"),
                        LocalDate.of(2026, 1, 1),
                        OnCallRateSegment.NO_END_DATE,
                        OnCallRateSegmentStatus.VALID,
                        Instant.parse("2026-01-02T00:00:00Z"));
        OnCallRateSegment saved = repository.save(open);

        assertThat(repository.findOpenSegment(CURVE))
                .map(OnCallRateSegment::getSegmentId)
                .contains(saved.getSegmentId());
    }

    @Test
    void findOpenSegment_ignoresCanceledSegmentWithNoEndDate() {
        OnCallRateSegment validOpen =
                new OnCallRateSegment(
                        UUID.randomUUID(),
                        CURVE,
                        new BigDecimal("3.00"),
                        LocalDate.of(2026, 1, 1),
                        OnCallRateSegment.NO_END_DATE,
                        OnCallRateSegmentStatus.VALID,
                        Instant.parse("2026-01-02T00:00:00Z"));
        OnCallRateSegment canceledPending =
                OnCallRateSegment.createPending(
                                UUID.randomUUID(), CURVE, new BigDecimal("3.50"), LocalDate.of(2026, 6, 1))
                        .cancel();
        repository.save(validOpen);
        repository.save(canceledPending);

        assertThat(repository.findOpenSegment(CURVE))
                .map(OnCallRateSegment::getSegmentId)
                .contains(validOpen.getSegmentId());
    }

    @Test
    void compareAndConfirmPending_transitionsRow() {
        UUID id = UUID.randomUUID();
        repository.save(
                OnCallRateSegment.createPending(
                        id, CURVE, new BigDecimal("3.50"), LocalDate.of(2026, 6, 1)));

        Instant validatedAt = Instant.parse("2026-05-31T12:00:00Z");
        assertThat(repository.compareAndConfirmPending(id, validatedAt)).isTrue();

        OnCallRateSegment loaded = repository.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(OnCallRateSegmentStatus.VALID);
        assertThat(loaded.getValidatedAt()).isEqualTo(validatedAt);
    }

    private void seedInstitution(String code) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        InstitutionEntity entity =
                institutionMapper.toEntity(new Institution(code, "HSBC", true), now);
        springDataInstitutionRepository.save(entity);
    }
}
