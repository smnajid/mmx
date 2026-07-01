package com.mmx.order.application.service;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.ReceiveOrderUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OpenPositionPort;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.OrganisationRepository;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.ExternalOrderReference;
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
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceiveOrderServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final OrganisationCode PM_ORG = new OrganisationCode("BNKG");
    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");

    @Mock
    OrderRepository orderRepository;

    @Mock
    AuditLogger auditLogger;

    @Mock
    Clock clock;

    @Mock
    ManagedCurrencyRepository managedCurrencyRepository;

    @Mock
    InstitutionRepository institutionRepository;

    @Mock
    OpenPositionPort openPositionPort;

    @Mock
    OrganisationRepository organisationRepository;

    @Mock
    LegalEntityRepository legalEntityRepository;

    ReceiveOrderService subject;

    @BeforeEach
    void freezeClock() {
        when(clock.now()).thenReturn(FIXED_NOW);
        when(clock.today()).thenReturn(TODAY);
        subject =
                new ReceiveOrderService(
                        orderRepository,
                        managedCurrencyRepository,
                        institutionRepository,
                        openPositionPort,
                        organisationRepository,
                        legalEntityRepository,
                        PM_ORG,
                        auditLogger,
                        clock);
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(permissiveEur()));
        when(managedCurrencyRepository.findByCode("USD")).thenReturn(Optional.of(permissiveUsd()));
        when(institutionRepository.findByInstitutionCode("BNKCO"))
                .thenReturn(Optional.of(new Institution("BNKCO", "BankCo", true)));
        when(organisationRepository.findByCode(PM_ORG)).thenReturn(Optional.of(new Organisation(PM_ORG)));
        when(legalEntityRepository.findByCode(LOC))
                .thenReturn(Optional.of(LegalEntity.tradingHub(LOC, PM_ORG)));
        when(legalEntityRepository.belongsToOrganisation(LOC, PM_ORG)).thenReturn(true);
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

    private static ManagedCurrency permissiveEur() {
        return new ManagedCurrency(
                "EUR",
                true,
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                EnumSet.allOf(Tenor.class),
                EnumSet.allOf(NoticePeriod.class));
    }

    @Test
    void receive_newOrder_isPersistedWithReceivedStatus() {
        var ref = new ExternalOrderReference("PM-recv-001");
        ReceiveOrderCommand command = validTermSubscribeCommand(ref);
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        ReceiveOrderUseCase.Result result = subject.receive(command);

        assertThat(result.newlyCreated()).isTrue();
        assertThat(result.status()).isEqualTo(OrderStatus.RECEIVED);

        ArgumentCaptor<MoneyMarketOrder> orderCaptor = ArgumentCaptor.forClass(MoneyMarketOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        MoneyMarketOrder persisted = orderCaptor.getValue();

        assertThat(result.orderId()).isEqualTo(persisted.getId());
        assertThat(persisted.getExternalOrderReference()).isEqualTo(ref);
        assertThat(persisted.getAmount()).isEqualByComparingTo(new BigDecimal("1000000.00"));
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
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        subject.receive(command);

        ArgumentCaptor<MoneyMarketOrder> orderCaptor = ArgumentCaptor.forClass(MoneyMarketOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getSourceContractNumber()).isNull();
    }

    @Test
    void receive_newOrder_logsOrderReceivedAudit() {
        var ref = new ExternalOrderReference("PM-audit-001");
        ReceiveOrderCommand command = validTermSubscribeCommand(ref);
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        ReceiveOrderUseCase.Result result = subject.receive(command);

        verify(auditLogger).log(
                eq(result.orderId()),
                eq(ReceiveOrderService.EVENT_ORDER_RECEIVED),
                eq(ReceiveOrderService.AUDIT_ACTOR_SYSTEM),
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

        ReceiveOrderCommand differentPayload =
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
                        "BNKCO");

        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.of(existing));

        ReceiveOrderUseCase.Result result = subject.receive(differentPayload);

        assertThat(result.orderId()).isEqualTo(existing.getId());
        assertThat(result.status()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(result.newlyCreated()).isFalse();
        assertThat(existing.getPortfolioNumber()).isEqualTo(new PortfolioNumber("PF-dup"));
        assertThat(existing.getAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));

        verify(orderRepository, never()).save(any());
        verify(managedCurrencyRepository, never()).findByCode(any());
        verify(openPositionPort, never()).findOpenByContractNumber(any());
        verify(auditLogger).log(
                eq(existing.getId()),
                eq(ReceiveOrderService.EVENT_DUPLICATE_RECEIVE_IGNORED),
                eq(ReceiveOrderService.AUDIT_ACTOR_SYSTEM),
                eq(FIXED_NOW));
    }

    @Test
    void receive_invalidCommand_propagatesDomainValidationAndDoesNotPersistOrAudit() {
        var ref = new ExternalOrderReference("PM-invalid-001");
        ReceiveOrderCommand invalidValueDate =
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
                        "BNKCO");

        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.receive(invalidValueDate)).isInstanceOf(InvalidOrderException.class);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    private static ReceiveOrderCommand validTermSubscribeCommand(ExternalOrderReference ref) {
        return new ReceiveOrderCommand(
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
                null,
                "BNKCO");
    }
}
