package com.mmx.order.adapter.out.persistence.termrate;

import com.mmx.order.adapter.out.persistence.JpaTermRateRepository;
import com.mmx.order.adapter.out.persistence.entity.InstitutionEntity;
import com.mmx.order.adapter.out.persistence.mapper.InstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.mapper.TermRatePersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataTermRateRepository;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JpaTermRateRepositoryTest {

    @Autowired
    SpringDataTermRateRepository springDataTermRateRepository;

    @Autowired
    SpringDataInstitutionRepository springDataInstitutionRepository;

    @Autowired
    InstitutionPersistenceMapper institutionMapper;

    @Autowired
    PlatformTransactionManager transactionManager;

    JpaTermRateRepository repository;

    @BeforeEach
    void setUp() {
        repository =
                new JpaTermRateRepository(
                        springDataTermRateRepository,
                        new TermRatePersistenceMapper(),
                        new TransactionTemplate(transactionManager));
        springDataTermRateRepository.deleteAll();
        springDataInstitutionRepository.deleteAll();
        seedInstitution("HSBC-01");
    }

    @Test
    void replaceAllForDate_replacesExistingRows() {
        LocalDate day = LocalDate.of(2026, 5, 30);
        Instant uploadedAt = Instant.parse("2026-05-30T08:00:00Z");

        repository.replaceAllForDate(
                day,
                List.of(
                        new TermRateAuditRow(
                                day,
                                "HSBC-01",
                                "EUR",
                                Tenor._1M,
                                new BigDecimal("3.25000000"),
                                uploadedAt,
                                "trader-1")));
        repository.replaceAllForDate(
                day,
                List.of(
                        new TermRateAuditRow(
                                day,
                                "HSBC-01",
                                "EUR",
                                Tenor._3M,
                                new BigDecimal("3.41000000"),
                                uploadedAt,
                                "trader-2")));

        List<TermRateAuditRow> rows = repository.findByTradingDate(day);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().tenor()).isEqualTo(Tenor._3M);
        assertThat(rows.getFirst().uploadedBy()).isEqualTo("trader-2");
    }

    @Test
    void findDistinctTradingDatesDesc_ordersNewestFirst() {
        Instant uploadedAt = Instant.parse("2026-05-30T08:00:00Z");
        repository.replaceAllForDate(
                LocalDate.of(2026, 5, 29),
                List.of(
                        row(LocalDate.of(2026, 5, 29), Tenor._1M, uploadedAt)));
        repository.replaceAllForDate(
                LocalDate.of(2026, 5, 30),
                List.of(
                        row(LocalDate.of(2026, 5, 30), Tenor._3M, uploadedAt)));

        assertThat(repository.findDistinctTradingDatesDesc())
                .containsExactly(LocalDate.of(2026, 5, 30), LocalDate.of(2026, 5, 29));
    }

    private TermRateAuditRow row(LocalDate day, Tenor tenor, Instant uploadedAt) {
        return new TermRateAuditRow(
                day,
                "HSBC-01",
                "EUR",
                tenor,
                new BigDecimal("3.25000000"),
                uploadedAt,
                "trader-1");
    }

    private void seedInstitution(String code) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        InstitutionEntity entity = institutionMapper.toEntity(new Institution(code, "HSBC", true), now);
        springDataInstitutionRepository.save(entity);
    }
}
