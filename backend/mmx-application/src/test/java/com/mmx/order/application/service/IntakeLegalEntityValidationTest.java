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
class IntakeLegalEntityValidationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final OrganisationCode PM_ORG = new OrganisationCode("BNKG");
    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

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
    void setUp() {
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
        when(managedCurrencyRepository.findByCode("EUR"))
                .thenReturn(Optional.of(permissiveEur()));
        when(institutionRepository.findByInstitutionCode("BNKCO"))
                .thenReturn(Optional.of(new Institution("BNKCO", "BankCo", true)));
        when(organisationRepository.findByCode(PM_ORG))
                .thenReturn(Optional.of(new Organisation(PM_ORG)));
        when(legalEntityRepository.findByCode(LOC))
                .thenReturn(Optional.of(LegalEntity.tradingHub(LOC, PM_ORG)));
        when(legalEntityRepository.belongsToOrganisation(LOC, PM_ORG)).thenReturn(true);
        when(legalEntityRepository.findByCode(PAR))
                .thenReturn(Optional.of(LegalEntity.tradingHub(PAR, PM_ORG)));
        when(legalEntityRepository.belongsToOrganisation(PAR, PM_ORG)).thenReturn(true);
    }

    @Test
    void receive_missingLegalEntityCode_rejected() {
        var ref = new ExternalOrderReference("PM-no-entity-001");
        ReceiveOrderCommand command = validCommand(ref, null);

        assertThatThrownBy(() -> subject.receive(command))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("legalEntityCode");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    @Test
    void receive_unknownLegalEntityCode_rejected() {
        var ref = new ExternalOrderReference("PM-unknown-entity-001");
        var unknown = new LegalEntityCode("ZZZ");
        ReceiveOrderCommand command = validCommand(ref, unknown);
        when(legalEntityRepository.findByCode(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.receive(command))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("legalEntityCode");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    @Test
    void receive_legalEntityNotInPmOrganisation_rejected() {
        var ref = new ExternalOrderReference("PM-foreign-entity-001");
        var foreign = new LegalEntityCode("SIN");
        ReceiveOrderCommand command = validCommand(ref, foreign);
        OrganisationCode otherOrg = new OrganisationCode("OTHR");
        when(legalEntityRepository.findByCode(foreign))
                .thenReturn(Optional.of(LegalEntity.tradingHub(foreign, otherOrg)));
        when(legalEntityRepository.belongsToOrganisation(foreign, PM_ORG)).thenReturn(false);

        assertThatThrownBy(() -> subject.receive(command))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Organisation");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    @Test
    void receive_sameExternalReferenceDifferentEntity_createsTwoOrders() {
        var ref = new ExternalOrderReference("PM-shared-ref-001");
        ReceiveOrderCommand locCommand = validCommand(ref, LOC);
        ReceiveOrderCommand parCommand = validCommand(ref, PAR);

        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());
        when(orderRepository.findByLegalEntityAndExternalReference(PAR, ref)).thenReturn(Optional.empty());
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        ReceiveOrderUseCase.Result locResult = subject.receive(locCommand);
        ReceiveOrderUseCase.Result parResult = subject.receive(parCommand);

        assertThat(locResult.newlyCreated()).isTrue();
        assertThat(parResult.newlyCreated()).isTrue();
        assertThat(locResult.orderId()).isNotEqualTo(parResult.orderId());

        ArgumentCaptor<MoneyMarketOrder> captor = ArgumentCaptor.forClass(MoneyMarketOrder.class);
        verify(orderRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(MoneyMarketOrder::getLegalEntityCode)
                .containsExactlyInAnyOrder(LOC, PAR);
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

        ReceiveOrderCommand command = validCommand(ref, LOC);
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref))
                .thenReturn(Optional.of(existing));

        ReceiveOrderUseCase.Result result = subject.receive(command);

        assertThat(result.orderId()).isEqualTo(existing.getId());
        assertThat(result.status()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(result.newlyCreated()).isFalse();
        verify(orderRepository, never()).save(any());
        verify(auditLogger).log(
                eq(existing.getId()),
                eq(ReceiveOrderService.EVENT_DUPLICATE_RECEIVE_IGNORED),
                eq(ReceiveOrderService.AUDIT_ACTOR_SYSTEM),
                eq(FIXED_NOW));
    }

    private static ReceiveOrderCommand validCommand(ExternalOrderReference ref, LegalEntityCode legalEntityCode) {
        return new ReceiveOrderCommand(
                ref,
                legalEntityCode,
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

    private static ManagedCurrency permissiveEur() {
        return new ManagedCurrency(
                "EUR",
                true,
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                EnumSet.allOf(Tenor.class),
                EnumSet.allOf(NoticePeriod.class));
    }
}
