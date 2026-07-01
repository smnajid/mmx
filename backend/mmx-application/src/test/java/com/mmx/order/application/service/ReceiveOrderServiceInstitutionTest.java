package com.mmx.order.application.service;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.ExecutedSubscriptionContractInfo;
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
import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceiveOrderServiceInstitutionTest {

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
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(permissiveEur()));
        when(organisationRepository.findByCode(PM_ORG)).thenReturn(Optional.of(new Organisation(PM_ORG)));
        when(legalEntityRepository.findByCode(LOC))
                .thenReturn(Optional.of(LegalEntity.tradingHub(LOC, PM_ORG)));
        when(legalEntityRepository.belongsToOrganisation(LOC, PM_ORG)).thenReturn(true);
    }

    @Test
    void receive_withValidActiveInstitutionCode_setsCounterpartyFromDisplayName() {
        var ref = new ExternalOrderReference("PM-inst-001");
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());
        when(institutionRepository.findByInstitutionCode("BNKCO"))
                .thenReturn(Optional.of(new Institution("BNKCO", "BankCo", true)));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        subject.receive(validCommand(ref, "BNKCO"));

        ArgumentCaptor<MoneyMarketOrder> orderCaptor = ArgumentCaptor.forClass(MoneyMarketOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        MoneyMarketOrder persisted = orderCaptor.getValue();

        assertThat(persisted.getInstitutionCode()).isEqualTo("BNKCO");
        assertThat(persisted.getCounterparty()).isEqualTo("BankCo");
    }

    @Test
    void receive_withUnknownInstitutionCode_isRejected() {
        var ref = new ExternalOrderReference("PM-inst-unknown");
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());
        when(institutionRepository.findByInstitutionCode("NOPE-01")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.receive(validCommand(ref, "NOPE-01")))
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

        assertThatThrownBy(() -> subject.receive(validCommand(ref, "DEAD-01")))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Institution is not active");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    @Test
    void receive_withoutInstitutionCode_isRejected() {
        var ref = new ExternalOrderReference("PM-inst-missing");
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.receive(validCommand(ref, null)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("institutionCode is required");

        verify(orderRepository, never()).save(any());
        verify(institutionRepository, never()).findByInstitutionCode(any());
        verifyNoInteractions(auditLogger);
    }

    @Test
    void receive_onCallIncreaseWithMatchingContractInstitution_succeeds() {
        var ref = new ExternalOrderReference("PM-lifecycle-match");
        when(orderRepository.findByLegalEntityAndExternalReference(LOC, ref)).thenReturn(Optional.empty());
        when(institutionRepository.findByInstitutionCode("BNKCO"))
                .thenReturn(Optional.of(new Institution("BNKCO", "BankCo", true)));
        when(orderRepository.findExecutedSubscriptionByContractNumber("CT-00042"))
                .thenReturn(
                        Optional.of(
                                new ExecutedSubscriptionContractInfo(
                                        "EUR", NoticePeriod._24H, "BNKCO", "BankCo")));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        subject.receive(onCallLifecycleCommand(ref, "BNKCO", "CT-00042", OrderOperation.INCREASE));

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

        assertThatThrownBy(() -> subject.receive(onCallLifecycleCommand(ref, "SGFR", "CT-00042", OrderOperation.INCREASE)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("must match the source contract institution");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    private static ReceiveOrderCommand onCallLifecycleCommand(
            ExternalOrderReference ref,
            String institutionCode,
            String sourceContractNumber,
            OrderOperation operation) {
        return new ReceiveOrderCommand(
                ref,
                LOC,
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

    private static ReceiveOrderCommand validCommand(ExternalOrderReference ref, String institutionCode) {
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
                institutionCode);
    }
}
