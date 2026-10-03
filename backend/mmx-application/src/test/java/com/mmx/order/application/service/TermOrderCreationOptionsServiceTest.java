package com.mmx.order.application.service;

import com.mmx.order.application.ordercreation.CounterpartiesResult;
import com.mmx.order.application.ordercreation.OperationsResult;
import com.mmx.order.application.ordercreation.OrderCreationCounterparty;
import com.mmx.order.application.ordercreation.OrderCreationOperation;
import com.mmx.order.application.ordercreation.TermCurrenciesResult;
import com.mmx.order.application.ordercreation.TenorsResult;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.support.InMemoryClientEnablementRepository;
import com.mmx.order.application.support.InMemoryInstitutionRepository;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.ClientEnablement;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("fast")
@ExtendWith(MockitoExtension.class)
class TermOrderCreationOptionsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 6);
    private static final Instant NOW = Instant.parse("2026-06-06T10:00:00Z");

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    @Mock
    ManagedCurrencyRepository managedCurrencyRepository;

    @Mock
    TermRateRepository termRateRepository;

    InMemoryInstitutionRepository institutionRepository;
    InMemoryClientEnablementRepository clientEnablementRepository;

    @Mock
    LegalEntityRepository legalEntityRepository;

    @Mock
    DelegatedGrantRepository delegatedGrantRepository;

    TermOrderCreationOptionsService subject;

    @BeforeEach
    void setUp() {
        institutionRepository = new InMemoryInstitutionRepository();
        clientEnablementRepository = new InMemoryClientEnablementRepository();
        Clock clock =
                new Clock() {
                    @Override
                    public Instant now() {
                        return NOW;
                    }

                    @Override
                    public LocalDate today() {
                        return TODAY;
                    }
                };
        subject =
                new TermOrderCreationOptionsService(
                        managedCurrencyRepository,
                        termRateRepository,
                        institutionRepository,
                        legalEntityRepository,
                        delegatedGrantRepository,
                        clientEnablementRepository,
                        clock);
    }

    @Test
    void listCurrencies_filtersByActiveManagedCurrencyEnabledTenorsAndRateExistence() {
        ManagedCurrency eur =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.of(Tenor._1M, Tenor._3M),
                        EnumSet.noneOf(NoticePeriod.class));
        ManagedCurrency chf =
                new ManagedCurrency(
                        "CHF",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.of(Tenor._1M),
                        EnumSet.noneOf(NoticePeriod.class));
        ManagedCurrency jpy =
                new ManagedCurrency(
                        "JPY",
                        false,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.of(Tenor._1M),
                        EnumSet.noneOf(NoticePeriod.class));
        ManagedCurrency gbp =
                new ManagedCurrency(
                        "GBP",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.noneOf(Tenor.class),
                        EnumSet.of(NoticePeriod._24H));

        when(legalEntityRepository.findByCode(LOC))
                .thenReturn(Optional.of(LegalEntity.tradingHub(LOC, new com.mmx.order.domain.model.OrganisationCode("LODH"))));
        when(managedCurrencyRepository.findAll()).thenReturn(List.of(eur, chf, jpy, gbp));
        when(termRateRepository.findDistinctCurrenciesWithTermRates()).thenReturn(List.of("EUR"));

        TermCurrenciesResult result = subject.listCurrencies(LOC);

        assertThat(result.tradingDate()).isEqualTo(TODAY);
        assertThat(result.currencies()).containsExactly("EUR");
    }

    @Test
    void listTenors_filtersByEnabledTenorsAndRateExistence() {
        ManagedCurrency eur =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.of(Tenor._1M, Tenor._3M, Tenor._6M),
                        EnumSet.noneOf(NoticePeriod.class));
        when(legalEntityRepository.findByCode(LOC))
                .thenReturn(Optional.of(LegalEntity.tradingHub(LOC, new com.mmx.order.domain.model.OrganisationCode("LODH"))));
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._1M))
                .thenReturn(List.of(rateRow("BNKCO", Tenor._1M, TODAY, "3.10")));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._3M))
                .thenReturn(List.of(rateRow("BNKCO", Tenor._3M, TODAY, "3.20")));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._6M)).thenReturn(List.of());

        TenorsResult result = subject.listTenors(LOC, "EUR");

        assertThat(result.tenors()).containsExactly(Tenor._1M, Tenor._3M);
    }

    @Test
    void listCounterparties_returnsLatestRatesWithIndicativeFlagSortedByRateDesc() {
        LocalDate yesterday = TODAY.minusDays(1);
        when(legalEntityRepository.findByCode(LOC))
                .thenReturn(Optional.of(LegalEntity.tradingHub(LOC, new com.mmx.order.domain.model.OrganisationCode("LODH"))));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._3M))
                .thenReturn(
                        List.of(
                                rateRow("BNKCO", Tenor._3M, TODAY, "3.45"),
                                rateRow("CDNRD", Tenor._3M, yesterday, "3.40")));
        institutionRepository.put(hubInstitution("BNKCO", "BankCo", true, "LOC-BNKCO-T"));
        institutionRepository.put(hubInstitution("CDNRD", "Canada Rd", true, "LOC-CDNRD-T"));

        CounterpartiesResult result = subject.listCounterparties(LOC, "EUR", Tenor._3M);

        assertThat(result.counterparties())
                .extracting(OrderCreationCounterparty::institutionCode)
                .containsExactly("BNKCO", "CDNRD");
        assertThat(result.counterparties().get(0))
                .satisfies(
                        cp -> {
                            assertThat(cp.displayName()).isEqualTo("BankCo");
                            assertThat(cp.rate()).isEqualByComparingTo("3.45");
                            assertThat(cp.rateDate()).isEqualTo(TODAY);
                            assertThat(cp.indicative()).isFalse();
                        });
        assertThat(result.counterparties().get(1))
                .satisfies(
                        cp -> {
                            assertThat(cp.displayName()).isEqualTo("Canada Rd");
                            assertThat(cp.rate()).isEqualByComparingTo("3.40");
                            assertThat(cp.rateDate()).isEqualTo(yesterday);
                            assertThat(cp.indicative()).isTrue();
                        });
    }

    @Test
    void listCounterparties_forHub_excludesInactiveInstitutionsAndThoseWithoutATermAccount() {
        when(legalEntityRepository.findByCode(LOC))
                .thenReturn(Optional.of(LegalEntity.tradingHub(LOC, new com.mmx.order.domain.model.OrganisationCode("LODH"))));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._3M))
                .thenReturn(
                        List.of(
                                rateRow("BNKCO", Tenor._3M, TODAY, "3.45"),
                                rateRow("DEAD", Tenor._3M, TODAY, "3.60"),
                                rateRow("NOACC", Tenor._3M, TODAY, "3.55")));
        institutionRepository.put(hubInstitution("BNKCO", "BankCo", true, "LOC-BNKCO-T"));
        institutionRepository.put(hubInstitution("DEAD", "Dead Bank", false, "LOC-DEAD-T"));
        institutionRepository.put(hubInstitution("NOACC", "No Account Bank", true, null));

        assertThat(subject.listCounterparties(LOC, "EUR", Tenor._3M).counterparties())
                .extracting(OrderCreationCounterparty::institutionCode)
                .containsExactly("BNKCO");
    }

    @Test
    void listCounterparties_forTradingClient_returnsOnlyOnboardedGrantedEnabledInstitutionsWithAccounts() {
        givenParClientWithHubRates();

        CounterpartiesResult result = subject.listCounterparties(PAR, "EUR", Tenor._3M);

        assertThat(result.counterparties())
                .extracting(OrderCreationCounterparty::institutionCode)
                .containsExactly("BNPLOC");
        assertThat(result.counterparties().getFirst().displayName()).isEqualTo("BNP Paribas via LOC");
        assertThat(result.counterparties().getFirst().rate()).isEqualByComparingTo("3.50");
    }

    @Test
    void listCounterparties_forTradingClient_excludesAGrantedTenorTheClientHasNotEnabled() {
        givenParClientWithHubRates();
        clientEnablementRepository.save(PAR, new ClientEnablement("BNPLOC", "EUR", Set.of(Tenor._1M), Set.of()));

        assertThat(subject.listCounterparties(PAR, "EUR", Tenor._3M).counterparties()).isEmpty();
    }

    @Test
    void listCounterparties_forTradingClient_excludesAnOffboardedInstitution() {
        givenParClientWithHubRates();
        institutionRepository.findByInstitutionCode("BNPLOC").orElseThrow().offboard();

        assertThat(subject.listCounterparties(PAR, "EUR", Tenor._3M).counterparties()).isEmpty();
    }

    @Test
    void listCounterparties_forTradingClient_excludesAnInstitutionWithoutTheClientTermAccount() {
        givenParClientWithHubRates();
        institutionRepository.findByInstitutionCode("BNPLOC").orElseThrow()
                .changeAccounts(CounterpartyAccounts.of(null, "PAR-BNP-OC"));

        assertThat(subject.listCounterparties(PAR, "EUR", Tenor._3M).counterparties()).isEmpty();
    }

    @Test
    void listCounterparties_forTradingClient_excludesWhenTheLinkedHubInstitutionHasNoTermAccount() {
        givenParClientWithHubRates();
        institutionRepository.put(hubInstitution("BNP", "BNP Paribas", true, null));

        assertThat(subject.listCounterparties(PAR, "EUR", Tenor._3M).counterparties()).isEmpty();
    }

    @Test
    void listCounterparties_forTradingClient_excludesAGrantedButNotOnboardedInstitution() {
        givenParClientWithHubRates();
        when(delegatedGrantRepository.findByClientLegalEntityCode(PAR))
                .thenReturn(List.of(grant("BNP"), grant("SGFR")));
        institutionRepository.put(hubInstitution("SGFR", "Societe Generale", true, "LOC-SGFR-T"));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._3M))
                .thenReturn(List.of(rateRow("BNP", Tenor._3M, TODAY, "3.50"), rateRow("SGFR", Tenor._3M, TODAY, "3.70")));

        assertThat(subject.listCounterparties(PAR, "EUR", Tenor._3M).counterparties())
                .extracting(OrderCreationCounterparty::institutionCode)
                .containsExactly("BNPLOC");
    }

    @Test
    void listCounterparties_forARemoteClient_offersTheOnboardedInstitution_whenTheHubInstitutionIsNotStoredLocally() {
        LegalEntity loc = LegalEntity.tradingHub(LOC, new com.mmx.order.domain.model.OrganisationCode("LODH"));
        LegalEntity par = LegalEntity.tradingClient(PAR, new com.mmx.order.domain.model.OrganisationCode("CGEG"), loc);
        when(legalEntityRepository.findByCode(PAR)).thenReturn(Optional.of(par));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._3M))
                .thenReturn(List.of(rateRow("HUBONLY", Tenor._3M, TODAY, "3.50")));
        when(delegatedGrantRepository.findByClientLegalEntityCode(PAR)).thenReturn(List.of(grant("HUBONLY")));
        institutionRepository.put(
                Institution.onboardFromGrant(
                        "HVL-01", "Hub Only", new HubInstitutionLink(LOC, "HUBONLY"), PAR, CounterpartyAccounts.of("PAR-HO-T", null)));
        clientEnablementRepository.save(PAR, new ClientEnablement("HVL-01", "EUR", Set.of(Tenor._3M), Set.of()));

        assertThat(subject.listCounterparties(PAR, "EUR", Tenor._3M).counterparties())
                .extracting(OrderCreationCounterparty::institutionCode)
                .containsExactly("HVL-01");
    }

    private static final LegalEntityCode CGD = new LegalEntityCode("CGD");

    @Test
    void listCurrencies_forARemoteClient_includesACurrencyThroughEffectiveEnablement_whenTheHubHasNoEnabledTenors() {
        givenCgdRemoteClientWithHubRates();
        when(managedCurrencyRepository.findAll()).thenReturn(List.of(activeCurrency("EUR", EnumSet.noneOf(Tenor.class))));

        assertThat(subject.listCurrencies(CGD).currencies()).containsExactly("EUR");
        verify(termRateRepository, never()).findDistinctCurrenciesWithTermRates();
    }

    @Test
    void listCurrencies_forARemoteClient_excludesACurrencyWithoutClientEnablement() {
        givenCgdRemoteClientWithHubRates();
        clientEnablementRepository.save(CGD, new ClientEnablement("HVL-01", "EUR", Set.of(), Set.of()));
        when(managedCurrencyRepository.findAll()).thenReturn(List.of(activeCurrency("EUR", EnumSet.of(Tenor._3M))));

        assertThat(subject.listCurrencies(CGD).currencies()).isEmpty();
    }

    @Test
    void listCurrencies_forARemoteClient_excludesAnOffboardedInstitution() {
        givenCgdRemoteClientWithHubRates();
        institutionRepository.findByInstitutionCode("HVL-01").orElseThrow().offboard();
        when(managedCurrencyRepository.findAll()).thenReturn(List.of(activeCurrency("EUR", EnumSet.of(Tenor._3M))));

        assertThat(subject.listCurrencies(CGD).currencies()).isEmpty();
    }

    @Test
    void listCurrencies_forARemoteClient_excludesACurrencyWhenTheHubHasNoRateForTheEnabledTenor() {
        givenCgdRemoteClientWithHubRates();
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._3M)).thenReturn(List.of());
        when(managedCurrencyRepository.findAll()).thenReturn(List.of(activeCurrency("EUR", EnumSet.of(Tenor._3M))));

        assertThat(subject.listCurrencies(CGD).currencies()).isEmpty();
    }

    @Test
    void listCurrencies_forAnUnknownLegalEntity_isEmpty() {
        when(legalEntityRepository.findByCode(CGD)).thenReturn(Optional.empty());

        assertThat(subject.listCurrencies(CGD).currencies()).isEmpty();
    }

    @Test
    void listTenors_forARemoteClient_intersectsGrantClientEnablementAndHubRates() {
        givenCgdRemoteClientWithHubRates();
        when(delegatedGrantRepository.findByClientLegalEntityCode(CGD))
                .thenReturn(List.of(grant(CGD, "BNP", EnumSet.of(Tenor._1M, Tenor._3M, Tenor._6M))));
        clientEnablementRepository.save(CGD, new ClientEnablement("HVL-01", "EUR", Set.of(Tenor._3M, Tenor._6M), Set.of()));
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(activeCurrency("EUR", EnumSet.noneOf(Tenor.class))));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._6M)).thenReturn(List.of());

        assertThat(subject.listTenors(CGD, "EUR").tenors()).containsExactly(Tenor._3M);
    }

    private void givenCgdRemoteClientWithHubRates() {
        LegalEntity loc = LegalEntity.tradingHub(LOC, new com.mmx.order.domain.model.OrganisationCode("LODH"));
        LegalEntity cgd = LegalEntity.tradingClient(CGD, new com.mmx.order.domain.model.OrganisationCode("CGEG"), loc);
        when(legalEntityRepository.findByCode(CGD)).thenReturn(Optional.of(cgd));
        lenient().when(delegatedGrantRepository.findByClientLegalEntityCode(CGD))
                .thenReturn(List.of(grant(CGD, "BNP", EnumSet.of(Tenor._3M))));
        lenient().when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._3M))
                .thenReturn(List.of(rateRow("BNP", Tenor._3M, TODAY, "3.50")));
        institutionRepository.put(
                Institution.onboardFromGrant(
                        "HVL-01", "BNP via LOC", new HubInstitutionLink(LOC, "BNP"), CGD, CounterpartyAccounts.of("CGD-BNP-T", null)));
        clientEnablementRepository.save(CGD, new ClientEnablement("HVL-01", "EUR", Set.of(Tenor._3M), Set.of()));
    }

    private static ManagedCurrency activeCurrency(String code, Set<Tenor> tenors) {
        Set<NoticePeriod> noticePeriods = tenors.isEmpty() ? EnumSet.of(NoticePeriod._24H) : EnumSet.noneOf(NoticePeriod.class);
        return new ManagedCurrency(code, true, new BigDecimal("500000"), new BigDecimal("100000"), tenors, noticePeriods);
    }

    private static DelegatedInstitutionGrant grant(LegalEntityCode client, String hubInstitutionCode, Set<Tenor> tenors) {
        return new DelegatedInstitutionGrant(
                hubInstitutionCode, client, "EUR", EnumSet.copyOf(tenors), EnumSet.noneOf(NoticePeriod.class), true);
    }

    private void givenParClientWithHubRates() {
        LegalEntity loc = LegalEntity.tradingHub(LOC, new com.mmx.order.domain.model.OrganisationCode("LODH"));
        LegalEntity par = LegalEntity.tradingClient(PAR, new com.mmx.order.domain.model.OrganisationCode("LODH"), loc);
        when(legalEntityRepository.findByCode(PAR)).thenReturn(Optional.of(par));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._3M))
                .thenReturn(
                        List.of(
                                rateRow("BNP", Tenor._3M, TODAY, "3.50"),
                                rateRow("BNKCO", Tenor._3M, TODAY, "3.45")));
        when(delegatedGrantRepository.findByClientLegalEntityCode(PAR)).thenReturn(List.of(grant("BNP")));
        institutionRepository.put(hubInstitution("BNP", "BNP Paribas", true, "LOC-BNP-T"));
        institutionRepository.put(hubInstitution("BNKCO", "BankCo", true, "LOC-BNKCO-T"));
        institutionRepository.put(
                Institution.onboardFromGrant(
                        "BNPLOC",
                        "BNP Paribas",
                        new HubInstitutionLink(LOC, "BNP"),
                        PAR,
                        CounterpartyAccounts.of("PAR-BNP-T", "PAR-BNP-OC")));
        clientEnablementRepository.save(PAR, new ClientEnablement("BNPLOC", "EUR", Set.of(Tenor._3M), Set.of()));
    }

    private static DelegatedInstitutionGrant grant(String hubInstitutionCode) {
        return new DelegatedInstitutionGrant(
                hubInstitutionCode, PAR, "EUR", EnumSet.of(Tenor._3M), EnumSet.noneOf(NoticePeriod.class), true);
    }

    private static Institution hubInstitution(String code, String name, boolean active, String termAccount) {
        return new Institution(code, name, LOC, null, CounterpartyAccounts.of(termAccount, null), active, 1);
    }

    @Test
    void listOperations_returnsSubscriptionWithMinSubscriptionAmount() {
        ManagedCurrency eur =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.of(Tenor._3M),
                        EnumSet.noneOf(NoticePeriod.class));
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

        OperationsResult result = subject.listOperations("EUR");

        assertThat(result.operations())
                .containsExactly(new OrderCreationOperation(OrderOperation.SUBSCRIPTION, new BigDecimal("500000")));
    }

    private static TermRateAuditRow rateRow(
            String institutionCode, Tenor tenor, LocalDate tradingDate, String rate) {
        return new TermRateAuditRow(
                tradingDate,
                institutionCode,
                "EUR",
                tenor,
                new BigDecimal(rate),
                NOW,
                "trader-1");
    }
}
