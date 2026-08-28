package com.mmx.order.application.service;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.IntakeUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.DelegatedGrantDirectory;
import com.mmx.order.application.port.out.ExecutedSubscriptionContractInfo;
import com.mmx.order.application.port.out.GlobalAccountDirectory;
import com.mmx.order.application.port.out.GrantResolution;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OpenPositionPort;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.OrganisationRepository;
import com.mmx.order.application.port.out.ProxyInstitutionRepository;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.Organisation;
import com.mmx.order.domain.model.OrganisationCode;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.ThinProxyInstitution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
@Tag("fast")

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntakeServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final OrganisationCode PM_ORG = new OrganisationCode("BNKG");
    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    @Mock OrderRepository orderRepository;
    @Mock AuditLogger auditLogger;
    @Mock Clock clock;
    @Mock ManagedCurrencyRepository managedCurrencyRepository;
    @Mock InstitutionRepository institutionRepository;
    @Mock ProxyInstitutionRepository proxyInstitutionRepository;
    @Mock OpenPositionPort openPositionPort;
    @Mock OrganisationRepository organisationRepository;
    @Mock LegalEntityRepository legalEntityRepository;
    @Mock DelegatedGrantDirectory delegatedGrantDirectory;
    @Mock GlobalAccountDirectory globalAccountDirectory;

    IntakeService subject;

    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(FIXED_NOW);
        when(clock.today()).thenReturn(TODAY);
        when(orderRepository.save(any())).thenAnswer(returnsFirstArg());
        when(organisationRepository.findByCode(PM_ORG)).thenReturn(Optional.of(new Organisation(PM_ORG)));
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(permissiveEur()));
        when(managedCurrencyRepository.findByCode("USD")).thenReturn(Optional.of(permissiveUsd()));
        when(institutionRepository.findByInstitutionCode("BNKCO"))
                .thenReturn(Optional.of(new Institution("BNKCO", "BankCo", true)));
        when(legalEntityRepository.findByCode(LOC))
                .thenReturn(Optional.of(LegalEntity.tradingHub(LOC, PM_ORG)));
        when(legalEntityRepository.belongsToOrganisation(LOC, PM_ORG)).thenReturn(true);
        LegalEntity hub = LegalEntity.tradingHub(LOC, PM_ORG);
        LegalEntity client = LegalEntity.tradingClient(PAR, PM_ORG, hub);
        when(legalEntityRepository.findByCode(PAR)).thenReturn(Optional.of(client));
        when(legalEntityRepository.belongsToOrganisation(PAR, PM_ORG)).thenReturn(true);
        when(proxyInstitutionRepository.findByInstitutionCode("BNPLOC"))
                .thenReturn(
                        Optional.of(
                                ThinProxyInstitution.forHubInstitution(
                                        "BNPLOC",
                                        new Institution("BNP", "BNP", true),
                                        LOC)));
        when(institutionRepository.findByInstitutionCode("BNP"))
                .thenReturn(Optional.of(new Institution("BNP", "BNP", true)));
        when(delegatedGrantDirectory.resolveTenor(PAR, "BNPLOC", "EUR", Tenor._3M))
                .thenReturn(GrantResolution.GRANTED);

        RoutedOrderIntake routedOrderIntake =
                new RoutedOrderIntake(
                        proxyInstitutionRepository,
                        delegatedGrantDirectory,
                        globalAccountDirectory,
                        institutionRepository,
                        orderRepository,
                        clock);
        subject =
                new IntakeService(
                        orderRepository,
                        managedCurrencyRepository,
                        institutionRepository,
                        openPositionPort,
                        organisationRepository,
                        legalEntityRepository,
                        PM_ORG,
                        routedOrderIntake,
                        auditLogger,
                        clock);
    }

    // --- Hub path (ReceiveOrderServiceTest) ---

    @Test
    void receive_newOrder_isPersistedWithReceivedStatus() {
        var ref = new ExternalOrderReference("PM-recv-001");
        ReceiveOrderCommand command = validTermSubscribeCommand(ref, LOC);
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());

        IntakeUseCase.Result result = subject.receive(command);

        assertThat(result.newlyCreated()).isTrue();
        assertThat(result.status()).isEqualTo(OrderStatus.RECEIVED);

        ArgumentCaptor<MoneyMarketOrder> orderCaptor = ArgumentCaptor.forClass(MoneyMarketOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(result.orderId()).isEqualTo(orderCaptor.getValue().getId());
    }

    @Test
    void receive_subscription_withSourceContractNumber_persistsNullSource() {
        var ref = new ExternalOrderReference("PM-sub-src-001");
        ReceiveOrderCommand command =
                new ReceiveOrderCommand(
                        ref,
                        LOC,
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        new PortfolioNumber("PF-1"),
                        "EUR",
                        new BigDecimal("1000000.00"),
                        TODAY.plusDays(5),
                        new BigDecimal("3.25000000"),
                        Tenor._3M,
                        null,
                        new ContractNumber("CN-should-not-stick"),
                        "BNKCO");
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());

        subject.receive(command);

        ArgumentCaptor<MoneyMarketOrder> orderCaptor = ArgumentCaptor.forClass(MoneyMarketOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getSourceContractNumber()).isNull();
    }

    @Test
    void receive_newOrder_logsOrderReceivedAudit() {
        var ref = new ExternalOrderReference("PM-audit-001");
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());

        IntakeUseCase.Result result = subject.receive(validTermSubscribeCommand(ref, LOC));

        verify(auditLogger)
                .log(
                        eq(result.orderId()),
                        eq(IntakeService.EVENT_ORDER_RECEIVED),
                        eq(IntakeService.AUDIT_ACTOR_SYSTEM),
                        eq(FIXED_NOW));
    }

    @Test
    void receive_duplicateExternalReference_returnsExistingWithoutSaveAndAuditIdempotentDuplicate() {
        var ref = new ExternalOrderReference("PM-dup-001");
        MoneyMarketOrder existing =
                MoneyMarketOrder.create(
                        ref,
                        LOC,
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        new PortfolioNumber("PF-dup"),
                        "EUR",
                        new BigDecimal("5000000.00"),
                        TODAY.plusDays(10),
                        new BigDecimal("2.50000000"),
                        Tenor._3M,
                        null,
                        null,
                        "BNKCO",
                        "BankCo",
                        TODAY);
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.of(existing));

        IntakeUseCase.Result result =
                subject.receive(
                        new ReceiveOrderCommand(
                                ref,
                                LOC,
                                OrderType.TERM,
                                OrderOperation.SUBSCRIPTION,
                                new PortfolioNumber("PF-other"),
                                "USD",
                                new BigDecimal("9000000.00"),
                                TODAY.plusDays(20),
                                new BigDecimal("4.25000000"),
                                Tenor._6M,
                                null,
                                null,
                                "BNKCO"));

        assertThat(result.newlyCreated()).isFalse();
        verify(orderRepository, never()).save(any());
        verify(auditLogger)
                .log(
                        eq(existing.getId()),
                        eq(IntakeService.EVENT_DUPLICATE_RECEIVE_IGNORED),
                        eq(IntakeService.AUDIT_ACTOR_SYSTEM),
                        eq(FIXED_NOW));
    }

    @Test
    void receive_invalidCommand_propagatesDomainValidationAndDoesNotPersistOrAudit() {
        var ref = new ExternalOrderReference("PM-invalid-001");
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                subject.receive(
                                        new ReceiveOrderCommand(
                                                ref,
                                                LOC,
                                                OrderType.TERM,
                                                OrderOperation.SUBSCRIPTION,
                                                new PortfolioNumber("PF-1"),
                                                "EUR",
                                                new BigDecimal("1000000.00"),
                                                TODAY.plusDays(1),
                                                new BigDecimal("3.00000000"),
                                                Tenor._1M,
                                                null,
                                                null,
                                                "BNKCO")))
                .isInstanceOf(InvalidOrderException.class);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    // --- Hub institution validation (ReceiveOrderServiceInstitutionTest) ---

    @Test
    void receive_withValidActiveInstitutionCode_setsCounterpartyFromDisplayName() {
        var ref = new ExternalOrderReference("PM-inst-001");
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());

        subject.receive(validTermSubscribeCommand(ref, LOC));

        ArgumentCaptor<MoneyMarketOrder> orderCaptor = ArgumentCaptor.forClass(MoneyMarketOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getCounterparty()).isEqualTo("BankCo");
    }

    @Test
    void receive_withUnknownInstitutionCode_isRejected() {
        var ref = new ExternalOrderReference("PM-inst-unknown");
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());
        when(institutionRepository.findByInstitutionCode("NOPE-01")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.receive(validTermSubscribeCommand(ref, LOC, "NOPE-01")))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Institution not found");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    @Test
    void receive_withInactiveInstitutionCode_isRejected() {
        var ref = new ExternalOrderReference("PM-inst-inactive");
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());
        when(institutionRepository.findByInstitutionCode("DEAD-01"))
                .thenReturn(Optional.of(new Institution("DEAD-01", "Dead Bank", false)));

        assertThatThrownBy(() -> subject.receive(validTermSubscribeCommand(ref, LOC, "DEAD-01")))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Institution is not active");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    @Test
    void receive_withoutInstitutionCode_isRejected() {
        var ref = new ExternalOrderReference("PM-inst-missing");
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.receive(validTermSubscribeCommand(ref, LOC, null)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("institutionCode is required");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    @Test
    void receive_onCallIncreaseWithMatchingContractInstitution_succeeds() {
        var ref = new ExternalOrderReference("PM-lifecycle-match");
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());
        when(orderRepository.findExecutedSubscriptionByContractNumber("CT-00042"))
                .thenReturn(
                        Optional.of(
                                new ExecutedSubscriptionContractInfo(
                                        "EUR", NoticePeriod._24H, "BNKCO", "BankCo")));

        subject.receive(onCallLifecycleCommand(ref, LOC, "BNKCO", "CT-00042", OrderOperation.INCREASE));

        verify(orderRepository).save(any(MoneyMarketOrder.class));
    }

    @Test
    void receive_onCallIncreaseWithMismatchedContractInstitution_isRejected() {
        var ref = new ExternalOrderReference("PM-lifecycle-mismatch");
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());
        when(institutionRepository.findByInstitutionCode("SGFR"))
                .thenReturn(Optional.of(new Institution("SGFR", "Société Générale", true)));
        when(orderRepository.findExecutedSubscriptionByContractNumber("CT-00042"))
                .thenReturn(
                        Optional.of(
                                new ExecutedSubscriptionContractInfo(
                                        "EUR", NoticePeriod._24H, "BNKCO", "BankCo")));

        assertThatThrownBy(
                        () ->
                                subject.receive(
                                        onCallLifecycleCommand(
                                                ref, LOC, "SGFR", "CT-00042", OrderOperation.INCREASE)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("must match the source contract institution");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    // --- Legal entity validation (IntakeLegalEntityValidationTest) ---

    @Test
    void receive_missingLegalEntityCode_rejected() {
        assertThatThrownBy(() -> subject.receive(validTermSubscribeCommand(new ExternalOrderReference("x"), null)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("legalEntityCode");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void receive_unknownLegalEntityCode_rejected() {
        var unknown = new LegalEntityCode("ZZZ");
        when(legalEntityRepository.findByCode(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                subject.receive(
                                        validTermSubscribeCommand(new ExternalOrderReference("x"), unknown)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("legalEntityCode");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void receive_legalEntityNotInPmOrganisation_rejected() {
        var foreign = new LegalEntityCode("SIN");
        OrganisationCode otherOrg = new OrganisationCode("OTHR");
        when(legalEntityRepository.findByCode(foreign))
                .thenReturn(Optional.of(LegalEntity.tradingHub(foreign, otherOrg)));
        when(legalEntityRepository.belongsToOrganisation(foreign, PM_ORG)).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                subject.receive(
                                        validTermSubscribeCommand(new ExternalOrderReference("x"), foreign)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Organisation");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void receive_sameExternalReferenceDifferentEntity_createsTwoOrders() {
        var ref = new ExternalOrderReference("PM-shared-ref-001");
        when(legalEntityRepository.findByCode(PAR))
                .thenReturn(Optional.of(LegalEntity.tradingHub(PAR, PM_ORG)));
        when(legalEntityRepository.belongsToOrganisation(PAR, PM_ORG)).thenReturn(true);
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());
        when(orderRepository.findByLegalEntityAndExternalReference(PAR, ref)).thenReturn(Optional.empty());

        IntakeUseCase.Result locResult = subject.receive(validTermSubscribeCommand(ref, LOC));
        IntakeUseCase.Result parResult = subject.receive(validTermSubscribeCommand(ref, PAR));

        assertThat(locResult.orderId()).isNotEqualTo(parResult.orderId());
        verify(orderRepository, times(2)).save(any(MoneyMarketOrder.class));
    }

    @Test
    void receive_duplicateWithinSameEntity_isIdempotent() {
        var ref = new ExternalOrderReference("PM-dup-entity-001");
        MoneyMarketOrder existing =
                MoneyMarketOrder.create(
                        ref,
                        LOC,
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        new PortfolioNumber("PF-dup"),
                        "EUR",
                        new BigDecimal("5000000.00"),
                        TODAY.plusDays(10),
                        new BigDecimal("2.50000000"),
                        Tenor._3M,
                        null,
                        null,
                        "BNKCO",
                        "BankCo",
                        TODAY);
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.of(existing));

        IntakeUseCase.Result result = subject.receive(validTermSubscribeCommand(ref, LOC));

        assertThat(result.newlyCreated()).isFalse();
        verify(orderRepository, never()).save(any());
    }

    // --- Routed path (RouteOrderServiceTest) ---

    @Test
    void successful_routing_yields_routed_client_and_hub_received() {
        when(globalAccountDirectory.resolve(PAR, LOC, "EUR"))
                .thenReturn(Optional.of(new GlobalAccount(PAR, LOC, "EUR", "PAR-EUR-001")));
        when(orderRepository.findByLegalEntityAndExternalReference(eq(PAR), any())).thenReturn(Optional.empty());
        when(orderRepository.findHubOrderByRoutingId(any())).thenReturn(Optional.empty());

        IntakeUseCase.Result result = subject.receive(sampleRoutedCommand());

        assertThat(result.newlyCreated()).isTrue();
        assertThat(result.status()).isEqualTo(OrderStatus.ROUTED);
        verify(orderRepository, times(2)).save(any(MoneyMarketOrder.class));
    }

    @Test
    void unresolved_account_rejects_without_hub_order() {
        when(globalAccountDirectory.resolve(PAR, LOC, "EUR")).thenReturn(Optional.empty());
        when(orderRepository.findByLegalEntityAndExternalReference(eq(PAR), any())).thenReturn(Optional.empty());

        IntakeUseCase.Result result = subject.receive(sampleRoutedCommand());

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        verify(orderRepository, times(1)).save(any(MoneyMarketOrder.class));
        verify(orderRepository, never()).findHubOrderByRoutingId(any());
    }

    @Test
    void grant_violation_rejects_without_hub_order() {
        when(delegatedGrantDirectory.resolveTenor(PAR, "BNPLOC", "EUR", Tenor._3M))
                .thenReturn(GrantResolution.NO_ACTIVE_GRANT);
        when(orderRepository.findByLegalEntityAndExternalReference(eq(PAR), any())).thenReturn(Optional.empty());

        IntakeUseCase.Result result = subject.receive(sampleRoutedCommand());

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        verify(orderRepository, times(1)).save(any(MoneyMarketOrder.class));
    }

    @Test
    void routed_onCallIncrease_withMismatchedContractInstitution_isRejectedWithoutOrders() {
        when(proxyInstitutionRepository.findByInstitutionCode("BNP-VIA-LOC"))
                .thenReturn(
                        Optional.of(
                                ThinProxyInstitution.forHubInstitution(
                                        "BNP-VIA-LOC",
                                        new Institution("BNP", "BNP", true),
                                        LOC)));
        when(orderRepository.findByLegalEntityAndExternalReference(eq(PAR), any())).thenReturn(Optional.empty());
        when(orderRepository.findExecutedSubscriptionByContractNumber("CT-00042"))
                .thenReturn(
                        Optional.of(
                                new ExecutedSubscriptionContractInfo(
                                        "EUR", NoticePeriod._24H, "SGFR-VIA-LOC", "SGFR")));
        when(delegatedGrantDirectory.resolveNotice(PAR, "BNP-VIA-LOC", "EUR", NoticePeriod._24H))
                .thenReturn(GrantResolution.GRANTED);

        assertThatThrownBy(
                        () ->
                                subject.receive(
                                        onCallLifecycleCommand(
                                                new ExternalOrderReference("PM-route-lifecycle-mismatch"),
                                                PAR,
                                                "BNP-VIA-LOC",
                                                "CT-00042",
                                                OrderOperation.INCREASE)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("must match the source contract institution");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    private static ReceiveOrderCommand sampleRoutedCommand() {
        return new ReceiveOrderCommand(
                new ExternalOrderReference("PM-ROUTE-100"),
                PAR,
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PAR-PM-77"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(2),
                new BigDecimal("2.50000000"),
                Tenor._3M,
                null,
                null,
                "BNPLOC");
    }

    private static ReceiveOrderCommand validTermSubscribeCommand(ExternalOrderReference ref, LegalEntityCode le) {
        return validTermSubscribeCommand(ref, le, "BNKCO");
    }

    private static ReceiveOrderCommand validTermSubscribeCommand(
            ExternalOrderReference ref, LegalEntityCode le, String institutionCode) {
        return new ReceiveOrderCommand(
                ref,
                le,
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-1"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                new BigDecimal("3.25000000"),
                Tenor._3M,
                null,
                null,
                institutionCode);
    }

    private static ReceiveOrderCommand onCallLifecycleCommand(
            ExternalOrderReference ref,
            LegalEntityCode legalEntityCode,
            String institutionCode,
            String sourceContractNumber,
            OrderOperation operation) {
        return new ReceiveOrderCommand(
                ref,
                legalEntityCode,
                OrderType.ON_CALL,
                operation,
                new PortfolioNumber("PF-1"),
                "EUR",
                new BigDecimal("100000.00"),
                TODAY.plusDays(5),
                null,
                null,
                NoticePeriod._24H,
                new ContractNumber(sourceContractNumber),
                institutionCode);
    }

    private static ManagedCurrency permissiveEur() {
        return new ManagedCurrency(
                "EUR",
                true,
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                EnumSet.allOf(Tenor.class),
                EnumSet.allOf(NoticePeriod.class));
    }

    private static ManagedCurrency permissiveUsd() {
        return new ManagedCurrency(
                "USD",
                true,
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                EnumSet.allOf(Tenor.class),
                EnumSet.allOf(NoticePeriod.class));
    }
}
