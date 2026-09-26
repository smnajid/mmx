package com.mmx.order.adapter.out.persistence.enablement;

import com.mmx.order.adapter.out.persistence.FixedScopeContextProvider;
import com.mmx.order.adapter.out.persistence.JpaClientEnablementRepository;
import com.mmx.order.adapter.out.persistence.JpaInstitutionRepository;
import com.mmx.order.adapter.out.persistence.PersistenceTestCleanup;
import com.mmx.order.adapter.out.persistence.mapper.InstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataClientEnablementRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataDelegatedGrantRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOnCallRateSegmentRepository;
import com.mmx.order.domain.model.ClientEnablement;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class JpaClientEnablementRepositoryIntegrationTest {

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate cleanupJdbc;

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    @Autowired SpringDataClientEnablementRepository springDataRepository;
    @Autowired SpringDataInstitutionRepository institutionSpringData;
    @Autowired SpringDataOnCallRateSegmentRepository onCallRateSegmentRepository;
    @Autowired SpringDataDelegatedGrantRepository grantRepository;
    @Autowired InstitutionPersistenceMapper institutionMapper;

    JpaClientEnablementRepository repository;

    @BeforeEach
    void setUp() {
        springDataRepository.deleteAll();
        PersistenceTestCleanup.clearInstitutionsAndDependents(cleanupJdbc);
        new JpaInstitutionRepository(institutionSpringData, institutionMapper, new FixedScopeContextProvider())
                .save(Institution.onboardFromGrant(
                        "BVL-01", "BNP", new HubInstitutionLink(LOC, "BNP"), PAR, CounterpartyAccounts.none()));
        repository = new JpaClientEnablementRepository(springDataRepository);
    }

    @AfterEach
    void tearDown() {
        springDataRepository.deleteAll();
    }

    @Test
    void readsAnEmptyEnablementWhenNoRowExists() {
        assertThat(repository.find("BVL-01", "EUR")).isEqualTo(ClientEnablement.empty("BVL-01", "EUR"));
        assertThat(repository.findByInstitutionCode("BVL-01")).isEmpty();
    }

    @Test
    void savesAndReadsBackPerCurrency() {
        repository.save(PAR, new ClientEnablement("BVL-01", "EUR", Set.of(Tenor._1M, Tenor._3M), Set.of(NoticePeriod._24H)));
        repository.save(PAR, new ClientEnablement("BVL-01", "USD", Set.of(Tenor._6M), Set.of()));

        assertThat(repository.find("BVL-01", "EUR"))
                .isEqualTo(new ClientEnablement("BVL-01", "EUR", Set.of(Tenor._1M, Tenor._3M), Set.of(NoticePeriod._24H)));
        assertThat(repository.findByInstitutionCode("BVL-01"))
                .extracting(ClientEnablement::currency)
                .containsExactlyInAnyOrder("EUR", "USD");
    }

    @Test
    void saveReplacesTheCurrencySets() {
        repository.save(PAR, new ClientEnablement("BVL-01", "EUR", Set.of(Tenor._1M, Tenor._3M), Set.of(NoticePeriod._24H)));

        repository.save(PAR, new ClientEnablement("BVL-01", "EUR", Set.of(Tenor._6M), Set.of()));

        assertThat(repository.find("BVL-01", "EUR"))
                .isEqualTo(new ClientEnablement("BVL-01", "EUR", Set.of(Tenor._6M), Set.of()));
        assertThat(springDataRepository.count()).isEqualTo(1);
    }
}
