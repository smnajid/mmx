package com.mmx.order.adapter.out.persistence.institution;

import com.mmx.order.adapter.out.persistence.FixedScopeContextProvider;
import com.mmx.order.adapter.out.persistence.JpaInstitutionRepository;
import com.mmx.order.adapter.out.persistence.JpaOrderRepository;
import com.mmx.order.adapter.out.persistence.PersistenceTestCleanup;
import com.mmx.order.adapter.out.persistence.mapper.InstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataDelegatedGrantRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOnCallRateSegmentRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.DealingReference;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutedHubOrderDraft;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TraderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class JpaInstitutionRepositoryIntegrationTest {

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate cleanupJdbc;

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    @Autowired SpringDataInstitutionRepository springDataRepository;
    @Autowired SpringDataOnCallRateSegmentRepository onCallRateSegmentRepository;
    @Autowired SpringDataDelegatedGrantRepository grantRepository;
    @Autowired SpringDataOrderRepository springDataOrderRepository;
    @Autowired InstitutionPersistenceMapper mapper;
    @Autowired OrderPersistenceMapper orderMapper;

    JpaInstitutionRepository repository;
    JpaOrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        repository = new JpaInstitutionRepository(springDataRepository, mapper, new FixedScopeContextProvider());
        orderRepository = new JpaOrderRepository(springDataOrderRepository, orderMapper);
        springDataOrderRepository.deleteAll();
        PersistenceTestCleanup.clearInstitutionsAndDependents(cleanupJdbc);
    }

    @Test
    void accountsOwnerAndVersionRoundTrip() {
        Institution hsbc =
                Institution.createNative("HSBC-01", "HSBC", LOC, CounterpartyAccounts.of("LOC-HSBC-T", "LOC-HSBC-OC"));
        repository.save(hsbc);

        Institution loaded = repository.findByInstitutionCode("HSBC-01").orElseThrow();
        assertThat(loaded.getOwningLegalEntityCode()).isEqualTo(LOC);
        assertThat(loaded.getCounterpartyAccounts()).isEqualTo(CounterpartyAccounts.of("LOC-HSBC-T", "LOC-HSBC-OC"));
        assertThat(loaded.getVersion()).isEqualTo(1);

        loaded.changeAccounts(CounterpartyAccounts.of(null, "LOC-HSBC-OC2"));
        loaded.deactivate();
        repository.save(loaded);

        Institution reloaded = repository.findByInstitutionCode("HSBC-01").orElseThrow();
        assertThat(reloaded.getCounterpartyAccounts()).isEqualTo(CounterpartyAccounts.of(null, "LOC-HSBC-OC2"));
        assertThat(reloaded.isClosedToNewBusiness()).isTrue();
        assertThat(reloaded.getVersion()).isEqualTo(3);
    }

    @Test
    void onboardedInstitutionIsStoredWithItsOwnerNotTheActiveScope() {
        repository.save(onboarded("BVL-01", "BNP"));

        assertThat(repository.findOnboardedByLegalEntityCode(PAR))
                .extracting(Institution::getInstitutionCode)
                .containsExactly("BVL-01");
        assertThat(repository.findOnboarded(PAR, new HubInstitutionLink(LOC, "BNP")))
                .map(Institution::getInstitutionCode)
                .contains("BVL-01");
        assertThat(repository.findNativeByLegalEntityCode(LOC)).isEmpty();
    }

    @Test
    void secondOnboardedRowForTheSameHubInstitutionViolatesTheUniqueIndex() {
        repository.save(onboarded("BVL-01", "BNP"));

        assertThatThrownBy(() -> repository.save(onboarded("BVL-02", "BNP")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nativeInstitutionsNeverCollideOnTheOnboardedUniqueIndex() {
        repository.save(Institution.createNative("HSBC-01", "HSBC", LOC, CounterpartyAccounts.none()));
        repository.save(Institution.createNative("BCI-01", "BCI", LOC, CounterpartyAccounts.none()));

        assertThat(repository.findNativeByLegalEntityCode(LOC)).hasSize(2);
    }

    @Test
    void onboardedRowWhoseHubInstitutionAndHubLegalEntityAreAbsentLocallyPersists() {
        Institution remote =
                Institution.onboardFromGrant(
                        "BVZ-01",
                        "BNP",
                        new HubInstitutionLink(new LegalEntityCode("ZZZ"), "NOT-HERE-01"),
                        PAR,
                        CounterpartyAccounts.of("PAR-BNP-T", null));

        repository.save(remote);

        Institution loaded = repository.findByInstitutionCode("BVZ-01").orElseThrow();
        assertThat(loaded.getHubLink())
                .contains(new HubInstitutionLink(new LegalEntityCode("ZZZ"), "NOT-HERE-01"));
        assertThat(loaded.getCounterpartyAccounts().term()).contains("PAR-BNP-T");
    }

    @Test
    void orderCounterpartyAccountSnapshotsRoundTrip() {
        Instant now = Instant.parse("2026-05-01T10:00:00Z");
        LocalDate today = LocalDate.of(2026, 5, 1);
        MoneyMarketOrder hubOrder =
                MoneyMarketOrder.createHubSideFromRouting(
                        new RoutedHubOrderDraft(
                                LOC,
                                new PortfolioNumber("PAR-EUR-001"),
                                "BNP",
                                "BNP",
                                "EUR",
                                new BigDecimal("1000000.00"),
                                today.plusDays(2),
                                OrderType.TERM,
                                OrderOperation.SUBSCRIPTION,
                                Tenor._3M,
                                null,
                                null,
                                null,
                                RoutingId.fromClientOrderId(UUID.randomUUID()),
                                PAR,
                                new ExternalOrderReference("PAR-PM-1"),
                                "PAR-BNP-T"),
                        today);
        hubOrder.assign(new TraderId("t-1"), now);
        hubOrder.execute(
                new BigDecimal("3.5"),
                "BNP",
                "BNP",
                "LOC-BNP-T",
                new DealingReference("DL-1"),
                new ContractNumber("CN-1"),
                new TraderId("t-1"),
                now);
        MoneyMarketOrder saved = orderRepository.save(hubOrder);

        MoneyMarketOrder loaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getCounterpartyAccount()).isEqualTo("LOC-BNP-T");
        assertThat(loaded.getClientCounterpartyAccount()).isEqualTo("PAR-BNP-T");
    }

    private static Institution onboarded(String code, String hubCode) {
        return Institution.onboardFromGrant(
                code, hubCode, new HubInstitutionLink(LOC, hubCode), PAR, CounterpartyAccounts.none());
    }
}
