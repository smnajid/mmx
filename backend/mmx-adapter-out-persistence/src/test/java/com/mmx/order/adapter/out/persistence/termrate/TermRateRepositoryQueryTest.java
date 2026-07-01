package com.mmx.order.adapter.out.persistence.termrate;

import com.mmx.order.adapter.out.persistence.JpaTermRateRepository;
import com.mmx.order.adapter.out.persistence.PersistenceTestCleanup;
import com.mmx.order.adapter.out.persistence.entity.InstitutionEntity;
import com.mmx.order.adapter.out.persistence.mapper.InstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.mapper.TermRatePersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataDelegatedGrantRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOnCallRateSegmentRepository;
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
class TermRateRepositoryQueryTest {

    @Autowired
    SpringDataTermRateRepository springDataTermRateRepository;

    @Autowired
    SpringDataInstitutionRepository springDataInstitutionRepository;

    @Autowired
    SpringDataOnCallRateSegmentRepository onCallRateSegmentRepository;

    @Autowired
    SpringDataDelegatedGrantRepository grantRepository;

    @Autowired
    InstitutionPersistenceMapper institutionMapper;

    @Autowired
    PlatformTransactionManager transactionManager;

    JpaTermRateRepository repository;

    private static final Instant UPLOADED_AT = Instant.parse("2026-06-06T08:00:00Z");

    @BeforeEach
    void setUp() {
        repository =
                new JpaTermRateRepository(
                        springDataTermRateRepository,
                        new TermRatePersistenceMapper(),
                        new TransactionTemplate(transactionManager));
        PersistenceTestCleanup.clearInstitutionsAndRates(
                grantRepository,
                onCallRateSegmentRepository,
                springDataTermRateRepository,
                springDataInstitutionRepository);
    }

    @Test
    void findLatestRatePerInstitution_returnsMostRecentRatePerInstitution() {
        seedInstitution("BNKCO", "BankCo", true);
        seedInstitution("CDNRD", "Canada Rd", true);
        repository.replaceAllForDate(
                LocalDate.of(2026, 6, 5),
                List.of(
                        rate(LocalDate.of(2026, 6, 5), "BNKCO", Tenor._3M, "3.40000000"),
                        rate(LocalDate.of(2026, 6, 5), "CDNRD", Tenor._3M, "3.35000000")));
        repository.replaceAllForDate(
                LocalDate.of(2026, 6, 6),
                List.of(rate(LocalDate.of(2026, 6, 6), "BNKCO", Tenor._3M, "3.45000000")));

        List<TermRateAuditRow> latest =
                repository.findLatestRatePerInstitution("EUR", Tenor._3M);

        assertThat(latest).hasSize(2);
        assertThat(latest)
                .extracting(TermRateAuditRow::institutionCode)
                .containsExactlyInAnyOrder("BNKCO", "CDNRD");
        assertThat(latest.stream().filter(row -> row.institutionCode().equals("BNKCO")).findFirst())
                .get()
                .satisfies(
                        row -> {
                            assertThat(row.tradingDate()).isEqualTo(LocalDate.of(2026, 6, 6));
                            assertThat(row.rate()).isEqualByComparingTo(new BigDecimal("3.45000000"));
                        });
        assertThat(latest.stream().filter(row -> row.institutionCode().equals("CDNRD")).findFirst())
                .get()
                .satisfies(
                        row -> {
                            assertThat(row.tradingDate()).isEqualTo(LocalDate.of(2026, 6, 5));
                            assertThat(row.rate()).isEqualByComparingTo(new BigDecimal("3.35000000"));
                        });
    }

    @Test
    void findLatestRatePerInstitution_excludesInactiveInstitutions() {
        seedInstitution("BNKCO", "BankCo", true);
        seedInstitution("DEAD-01", "Dead Bank", false);
        repository.replaceAllForDate(
                LocalDate.of(2026, 6, 6),
                List.of(
                        rate(LocalDate.of(2026, 6, 6), "BNKCO", Tenor._3M, "3.45000000"),
                        rate(LocalDate.of(2026, 6, 6), "DEAD-01", Tenor._3M, "3.60000000")));

        List<TermRateAuditRow> latest =
                repository.findLatestRatePerInstitution("EUR", Tenor._3M);

        assertThat(latest).extracting(TermRateAuditRow::institutionCode).containsExactly("BNKCO");
    }

    @Test
    void findDistinctCurrenciesWithTermRates_returnsCurrenciesFromActiveInstitutionsOnly() {
        seedInstitution("BNKCO", "BankCo", true);
        seedInstitution("DEAD-01", "Dead Bank", false);
        repository.replaceAllForDate(
                LocalDate.of(2026, 6, 6),
                List.of(
                        rate(LocalDate.of(2026, 6, 6), "BNKCO", Tenor._3M, "3.45000000", "EUR"),
                        rate(LocalDate.of(2026, 6, 6), "DEAD-01", Tenor._3M, "3.60000000", "USD")));

        assertThat(repository.findDistinctCurrenciesWithTermRates()).containsExactly("EUR");
    }

    private TermRateAuditRow rate(LocalDate day, String institution, Tenor tenor, String rateValue) {
        return rate(day, institution, tenor, rateValue, "EUR");
    }

    private TermRateAuditRow rate(
            LocalDate day, String institution, Tenor tenor, String rateValue, String currency) {
        return new TermRateAuditRow(
                day,
                institution,
                currency,
                tenor,
                new BigDecimal(rateValue),
                UPLOADED_AT,
                "trader-1");
    }

    private void seedInstitution(String code, String displayName, boolean active) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        InstitutionEntity entity =
                institutionMapper.toEntity(new Institution(code, displayName, active), now);
        springDataInstitutionRepository.save(entity);
    }
}
