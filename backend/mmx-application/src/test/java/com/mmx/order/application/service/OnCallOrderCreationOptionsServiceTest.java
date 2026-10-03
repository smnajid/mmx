package com.mmx.order.application.service;

import com.mmx.order.application.exception.ContractNotFoundException;
import com.mmx.order.application.ordercreation.ContractInfoResult;
import com.mmx.order.application.ordercreation.CounterpartiesResult;
import com.mmx.order.application.ordercreation.NoticePeriodsResult;
import com.mmx.order.application.ordercreation.OnCallCurrenciesResult;
import com.mmx.order.application.ordercreation.OperationsResult;
import com.mmx.order.application.ordercreation.OrderCreationCounterparty;
import com.mmx.order.application.ordercreation.OrderCreationOperation;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.ExecutedSubscriptionContractInfo;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.support.InMemoryClientEnablementRepository;
import com.mmx.order.application.support.InMemoryInstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.ClientEnablement;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OnCallRateSegmentStatus;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("fast")
@ExtendWith(MockitoExtension.class)
class OnCallOrderCreationOptionsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 6);
    private static final LocalDate VALUE_DATE = LocalDate.of(2026, 6, 9);

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    @Mock
    ManagedCurrencyRepository managedCurrencyRepository;

    @Mock
    OnCallRateRepository onCallRateRepository;

    InMemoryInstitutionRepository institutionRepository;
    InMemoryClientEnablementRepository clientEnablementRepository;

    @Mock
    OrderRepository orderRepository;

    @Mock
    LegalEntityRepository legalEntityRepository;

    @Mock
    DelegatedGrantRepository delegatedGrantRepository;

    OnCallOrderCreationOptionsService subject;

    @BeforeEach
    void setUp() {
        institutionRepository = new InMemoryInstitutionRepository();
        clientEnablementRepository = new InMemoryClientEnablementRepository();
        subject =
                new OnCallOrderCreationOptionsService(
                        managedCurrencyRepository,
                        onCallRateRepository,
                        institutionRepository,
                        orderRepository,
                        legalEntityRepository,
                        delegatedGrantRepository,
                        clientEnablementRepository);
    }

    @Test
    void listCurrencies_filtersByActiveManagedCurrencyEnabledNoticePeriodsAndOpenSegments() {
        ManagedCurrency eur =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.noneOf(Tenor.class),
                        EnumSet.of(NoticePeriod._24H));
        ManagedCurrency usd =
                new ManagedCurrency(
                        "USD",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.noneOf(Tenor.class),
                        EnumSet.of(NoticePeriod._24H));
        when(legalEntityRepository.findByCode(LOC))
                .thenReturn(Optional.of(LegalEntity.tradingHub(LOC, new com.mmx.order.domain.model.OrganisationCode("LODH"))));
        when(managedCurrencyRepository.findAll()).thenReturn(List.of(eur, usd));
        when(onCallRateRepository.findDistinctCurrenciesWithOpenOnCallSegments()).thenReturn(List.of("EUR"));

        OnCallCurrenciesResult result = subject.listCurrencies(LOC);

        assertThat(result.currencies()).containsExactly("EUR");
    }

    @Test
    void listNoticePeriods_filtersByEnabledNoticePeriodsAndOpenSegmentExistence() {
        ManagedCurrency eur =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.noneOf(Tenor.class),
                        EnumSet.of(NoticePeriod._24H, NoticePeriod._48H));
        when(legalEntityRepository.findByCode(LOC))
                .thenReturn(Optional.of(LegalEntity.tradingHub(LOC, new com.mmx.order.domain.model.OrganisationCode("LODH"))));
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));
        when(onCallRateRepository.findOpenSegmentsByCurrencyAndNoticePeriod("EUR", NoticePeriod._24H))
                .thenReturn(List.of(openSegment("BNKCO", NoticePeriod._24H, "2.85")));
        when(onCallRateRepository.findOpenSegmentsByCurrencyAndNoticePeriod("EUR", NoticePeriod._48H))
                .thenReturn(List.of());

        NoticePeriodsResult result = subject.listNoticePeriods(LOC, "EUR");

        assertThat(result.noticePeriods()).containsExactly(NoticePeriod._24H);
    }

    @Test
    void listCounterparties_returnsSegmentRatesForValueDateSortedByRateDesc() {
        when(legalEntityRepository.findByCode(LOC))
                .thenReturn(Optional.of(LegalEntity.tradingHub(LOC, new com.mmx.order.domain.model.OrganisationCode("LODH"))));
        when(onCallRateRepository.findSegmentsCoveringDate("EUR", NoticePeriod._24H, VALUE_DATE))
                .thenReturn(
                        List.of(
                                openSegment("BNKCO", NoticePeriod._24H, "2.90"),
                                openSegment("CDNRD", NoticePeriod._24H, "2.85")));
        institutionRepository.put(hubInstitution("BNKCO", "BankCo", true, "LOC-BNKCO-OC"));
        institutionRepository.put(hubInstitution("CDNRD", "Canada Rd", true, "LOC-CDNRD-OC"));

        CounterpartiesResult result =
                subject.listCounterparties(LOC, "EUR", NoticePeriod._24H, VALUE_DATE);

        assertThat(result.counterparties())
                .extracting(OrderCreationCounterparty::institutionCode)
                .containsExactly("BNKCO", "CDNRD");
        assertThat(result.counterparties().get(0).rate()).isEqualByComparingTo("2.90");
        assertThat(result.counterparties().get(1).rate()).isEqualByComparingTo("2.85");
    }

    @Test
    void listCounterparties_forHub_excludesInstitutionsWithoutAnOnCallAccount() {
        when(legalEntityRepository.findByCode(LOC))
                .thenReturn(Optional.of(LegalEntity.tradingHub(LOC, new com.mmx.order.domain.model.OrganisationCode("LODH"))));
        when(onCallRateRepository.findSegmentsCoveringDate("EUR", NoticePeriod._24H, VALUE_DATE))
                .thenReturn(List.of(openSegment("BNKCO", NoticePeriod._24H, "2.90"), openSegment("NOACC", NoticePeriod._24H, "2.95")));
        institutionRepository.put(hubInstitution("BNKCO", "BankCo", true, "LOC-BNKCO-OC"));
        institutionRepository.put(hubInstitution("NOACC", "No Account", true, null));

        assertThat(subject.listCounterparties(LOC, "EUR", NoticePeriod._24H, VALUE_DATE).counterparties())
                .extracting(OrderCreationCounterparty::institutionCode)
                .containsExactly("BNKCO");
    }

    @Test
    void listCounterparties_forTradingClient_returnsOnlyOnboardedGrantedEnabledInstitutionsWithAccounts() {
        givenParClient();

        assertThat(subject.listCounterparties(PAR, "EUR", NoticePeriod._24H, VALUE_DATE).counterparties())
                .extracting(OrderCreationCounterparty::institutionCode, OrderCreationCounterparty::displayName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("BNPLOC", "BNP Paribas via LOC"));
    }

    @Test
    void listCounterparties_forTradingClient_excludesAnOffboardedInstitution() {
        givenParClient();
        institutionRepository.findByInstitutionCode("BNPLOC").orElseThrow().offboard();

        assertThat(subject.listCounterparties(PAR, "EUR", NoticePeriod._24H, VALUE_DATE).counterparties()).isEmpty();
    }

    @Test
    void listCounterparties_forTradingClient_excludesANoticePeriodTheClientHasNotEnabled() {
        givenParClient();
        clientEnablementRepository.save(PAR, new ClientEnablement("BNPLOC", "EUR", Set.of(), Set.of(NoticePeriod._48H)));

        assertThat(subject.listCounterparties(PAR, "EUR", NoticePeriod._24H, VALUE_DATE).counterparties()).isEmpty();
    }

    @Test
    void listCounterparties_forTradingClient_excludesMissingClientOrHubOnCallAccount() {
        givenParClient();
        institutionRepository.put(hubInstitution("BNP", "BNP Paribas", true, null));

        assertThat(subject.listCounterparties(PAR, "EUR", NoticePeriod._24H, VALUE_DATE).counterparties()).isEmpty();

        institutionRepository.put(hubInstitution("BNP", "BNP Paribas", true, "LOC-BNP-OC"));
        institutionRepository.findByInstitutionCode("BNPLOC").orElseThrow()
                .changeAccounts(CounterpartyAccounts.of("PAR-BNP-T", null));

        assertThat(subject.listCounterparties(PAR, "EUR", NoticePeriod._24H, VALUE_DATE).counterparties()).isEmpty();
    }

    private static final LegalEntityCode CGD = new LegalEntityCode("CGD");

    @Test
    void listCurrencies_forARemoteClient_includesACurrencyThroughAPendingConfirmationSegmentAndEffectiveEnablement() {
        givenCgdRemoteClient(Set.of(NoticePeriod._24H), Set.of(NoticePeriod._24H));
        when(managedCurrencyRepository.findAll()).thenReturn(List.of(activeTermOnlyCurrency("EUR")));
        when(onCallRateRepository.findOpenSegmentsByCurrencyAndNoticePeriod("EUR", NoticePeriod._24H))
                .thenReturn(List.of(segment("BNP", NoticePeriod._24H, "2.90", OnCallRateSegmentStatus.PENDING_CONFIRMATION)));

        assertThat(subject.listCurrencies(CGD).currencies()).containsExactly("EUR");
        verify(onCallRateRepository, never()).findDistinctCurrenciesWithOpenOnCallSegments();
    }

    @Test
    void listCurrencies_forARemoteClient_excludesACurrencyWithoutClientEnablement() {
        givenCgdRemoteClient(Set.of(NoticePeriod._24H), Set.of());
        when(managedCurrencyRepository.findAll()).thenReturn(List.of(activeTermOnlyCurrency("EUR")));

        assertThat(subject.listCurrencies(CGD).currencies()).isEmpty();
    }

    @Test
    void listCurrencies_forARemoteClient_excludesAnOffboardedInstitution() {
        givenCgdRemoteClient(Set.of(NoticePeriod._24H), Set.of(NoticePeriod._24H));
        institutionRepository.findByInstitutionCode("HVL-01").orElseThrow().offboard();
        when(managedCurrencyRepository.findAll()).thenReturn(List.of(activeTermOnlyCurrency("EUR")));

        assertThat(subject.listCurrencies(CGD).currencies()).isEmpty();
    }

    @Test
    void listNoticePeriods_forARemoteClient_intersectsGrantClientEnablementAndHubSegments() {
        givenCgdRemoteClient(Set.of(NoticePeriod._24H, NoticePeriod._48H), Set.of(NoticePeriod._48H));
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(activeTermOnlyCurrency("EUR")));
        when(onCallRateRepository.findOpenSegmentsByCurrencyAndNoticePeriod("EUR", NoticePeriod._48H))
                .thenReturn(List.of(segment("BNP", NoticePeriod._48H, "2.95", OnCallRateSegmentStatus.VALID)));

        assertThat(subject.listNoticePeriods(CGD, "EUR").noticePeriods()).containsExactly(NoticePeriod._48H);
    }

    @Test
    void listCurrencies_forAnUnknownLegalEntity_isEmpty() {
        when(legalEntityRepository.findByCode(CGD)).thenReturn(Optional.empty());

        assertThat(subject.listCurrencies(CGD).currencies()).isEmpty();
    }

    @Test
    void listNoticePeriods_forAnUnknownLegalEntity_isEmpty() {
        when(legalEntityRepository.findByCode(CGD)).thenReturn(Optional.empty());

        assertThat(subject.listNoticePeriods(CGD, "EUR").noticePeriods()).isEmpty();
    }

    @Test
    void listNoticePeriods_forARemoteClient_isEmptyForAnInactiveHubCurrency() {
        givenCgdRemoteClient(Set.of(NoticePeriod._24H), Set.of(NoticePeriod._24H));
        when(managedCurrencyRepository.findByCode("EUR"))
                .thenReturn(Optional.of(new ManagedCurrency(
                        "EUR", false, new BigDecimal("500000"), new BigDecimal("100000"), EnumSet.noneOf(Tenor.class), EnumSet.of(NoticePeriod._24H))));

        assertThat(subject.listNoticePeriods(CGD, "EUR").noticePeriods()).isEmpty();
    }

    private void givenCgdRemoteClient(Set<NoticePeriod> granted, Set<NoticePeriod> clientEnabled) {
        LegalEntity loc = LegalEntity.tradingHub(LOC, new com.mmx.order.domain.model.OrganisationCode("LODH"));
        LegalEntity cgd = LegalEntity.tradingClient(CGD, new com.mmx.order.domain.model.OrganisationCode("CGEG"), loc);
        when(legalEntityRepository.findByCode(CGD)).thenReturn(Optional.of(cgd));
        lenient().when(delegatedGrantRepository.findByClientLegalEntityCode(CGD))
                .thenReturn(List.of(new DelegatedInstitutionGrant("BNP", CGD, "EUR", EnumSet.noneOf(Tenor.class), EnumSet.copyOf(granted), true)));
        institutionRepository.put(
                Institution.onboardFromGrant(
                        "HVL-01", "BNP via LOC", new HubInstitutionLink(LOC, "BNP"), CGD, CounterpartyAccounts.of(null, "CGD-BNP-OC")));
        clientEnablementRepository.save(CGD, new ClientEnablement("HVL-01", "EUR", Set.of(), clientEnabled));
        lenient().when(onCallRateRepository.findOpenSegmentsByCurrencyAndNoticePeriod(org.mockito.ArgumentMatchers.eq("EUR"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
    }

    private static ManagedCurrency activeTermOnlyCurrency(String code) {
        // The hub's own OnCall enablement is irrelevant to a client; only its active flag matters.
        return new ManagedCurrency(
                code, true, new BigDecimal("500000"), new BigDecimal("100000"), EnumSet.of(Tenor._3M), EnumSet.noneOf(NoticePeriod.class));
    }

    private static OnCallRateSegment segment(String institutionCode, NoticePeriod noticePeriod, String rate, OnCallRateSegmentStatus status) {
        return new OnCallRateSegment(
                UUID.randomUUID(),
                new OnCallCurveKey(institutionCode, "EUR", noticePeriod),
                new BigDecimal(rate),
                LocalDate.of(2026, 6, 1),
                OnCallRateSegment.NO_END_DATE,
                status,
                null);
    }

    private void givenParClient() {
        LegalEntity loc = LegalEntity.tradingHub(LOC, new com.mmx.order.domain.model.OrganisationCode("LODH"));
        LegalEntity par = LegalEntity.tradingClient(PAR, new com.mmx.order.domain.model.OrganisationCode("LODH"), loc);
        when(legalEntityRepository.findByCode(PAR)).thenReturn(Optional.of(par));
        when(onCallRateRepository.findSegmentsCoveringDate("EUR", NoticePeriod._24H, VALUE_DATE))
                .thenReturn(List.of(openSegment("BNP", NoticePeriod._24H, "2.90"), openSegment("SGFR", NoticePeriod._24H, "2.99")));
        when(delegatedGrantRepository.findByClientLegalEntityCode(PAR))
                .thenReturn(List.of(
                        new DelegatedInstitutionGrant("BNP", PAR, "EUR", EnumSet.noneOf(Tenor.class), EnumSet.of(NoticePeriod._24H), true),
                        new DelegatedInstitutionGrant("SGFR", PAR, "EUR", EnumSet.noneOf(Tenor.class), EnumSet.of(NoticePeriod._24H), true)));
        institutionRepository.put(hubInstitution("BNP", "BNP Paribas", true, "LOC-BNP-OC"));
        institutionRepository.put(hubInstitution("SGFR", "Societe Generale", true, "LOC-SGFR-OC"));
        institutionRepository.put(
                Institution.onboardFromGrant(
                        "BNPLOC",
                        "BNP Paribas",
                        new HubInstitutionLink(LOC, "BNP"),
                        PAR,
                        CounterpartyAccounts.of("PAR-BNP-T", "PAR-BNP-OC")));
        clientEnablementRepository.save(PAR, new ClientEnablement("BNPLOC", "EUR", Set.of(), Set.of(NoticePeriod._24H)));
    }

    private static Institution hubInstitution(String code, String name, boolean active, String onCallAccount) {
        return new Institution(code, name, LOC, null, CounterpartyAccounts.of(null, onCallAccount), active, 1);
    }

    @Test
    void listOperations_returnsAllFourWithCorrectMinAmounts() {
        ManagedCurrency eur =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.noneOf(Tenor.class),
                        EnumSet.of(NoticePeriod._24H));
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

        OperationsResult result = subject.listOperations("EUR");

        assertThat(result.operations())
                .containsExactly(
                        new OrderCreationOperation(OrderOperation.SUBSCRIPTION, new BigDecimal("500000")),
                        new OrderCreationOperation(OrderOperation.INCREASE, new BigDecimal("100000")),
                        new OrderCreationOperation(OrderOperation.DECREASE, new BigDecimal("100000")),
                        new OrderCreationOperation(OrderOperation.REDEMPTION, new BigDecimal("100000")));
    }

    @Test
    void getContractInfo_returnsCurrencyNoticePeriodInstitutionAndCounterpartyFromExecutedSubscription() {
        when(orderRepository.findExecutedSubscriptionByContractNumber("CT-00042"))
                .thenReturn(
                        Optional.of(
                                new ExecutedSubscriptionContractInfo(
                                        "EUR", NoticePeriod._24H, "BNKCO", "BankCo")));

        ContractInfoResult result = subject.getContractInfo("CT-00042");

        assertThat(result.currency()).isEqualTo("EUR");
        assertThat(result.noticePeriod()).isEqualTo(NoticePeriod._24H);
        assertThat(result.institutionCode()).isEqualTo("BNKCO");
        assertThat(result.counterparty()).isEqualTo("BankCo");
    }

    @Test
    void getContractInfo_throwsWhenContractNotFound() {
        when(orderRepository.findExecutedSubscriptionByContractNumber("CT-99999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.getContractInfo("CT-99999"))
                .isInstanceOf(ContractNotFoundException.class);
    }

    private static OnCallRateSegment openSegment(String institutionCode, NoticePeriod noticePeriod, String rate) {
        return new OnCallRateSegment(
                UUID.randomUUID(),
                new OnCallCurveKey(institutionCode, "EUR", noticePeriod),
                new BigDecimal(rate),
                LocalDate.of(2026, 6, 1),
                OnCallRateSegment.NO_END_DATE,
                OnCallRateSegmentStatus.VALID,
                null);
    }
}
