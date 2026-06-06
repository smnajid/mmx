package com.mmx.order.application.service;

import com.mmx.order.application.command.ExecuteOrderCommand;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.ExecutionHandoffOutbox;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.policy.OrderAgainstInstitutionPolicy;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.exception.UnauthorizedTraderException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;
import com.mmx.order.domain.model.Assignment;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TraderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

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
class ExecuteOrderServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final TraderId TRADER_A = new TraderId("trader-a");
    private static final TraderId TRADER_B = new TraderId("trader-b");

    private static final DealingReference DEAL_REF = new DealingReference("DL-test-001");
    private static final ContractNumber CONTRACT_REF = new ContractNumber("CN-test-001");
    private static final ContractNumber LIFECYCLE_SOURCE_REF = new ContractNumber("CN-lifecycle-src");

    @Mock
    OrderRepository orderRepository;

    @Mock
    ReferenceGenerator referenceGenerator;

    @Mock
    AuditLogger auditLogger;

    @Mock
    Clock clock;

    @Mock
    ExecutionHandoffOutbox executionHandoffOutbox;

    @Mock
    InstitutionRepository institutionRepository;

    private final OrderAgainstInstitutionPolicy institutionPolicy = new OrderAgainstInstitutionPolicy();

    @InjectMocks
    ExecuteOrderService subject;

    private static final Institution HSBC =
            new Institution("HSBC-01", "BankCo International", true);

    @BeforeEach
    void freezeClock() {
        when(clock.now()).thenReturn(FIXED_NOW);
        when(clock.today()).thenReturn(TODAY);
        when(institutionRepository.existsAny()).thenReturn(true);
        when(institutionRepository.findByInstitutionCode("HSBC-01")).thenReturn(Optional.of(HSBC));
        subject =
                new ExecuteOrderService(
                        orderRepository,
                        institutionRepository,
                        institutionPolicy,
                        referenceGenerator,
                        auditLogger,
                        clock,
                        executionHandoffOutbox);
    }

    @Test
    void execute_success_generates_refs_uses_clock_and_audits() {
        MoneyMarketOrder assigned = receivedOrder();
        assigned.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());
        when(referenceGenerator.generateDealingReference()).thenReturn(DEAL_REF);
        when(referenceGenerator.generateContractNumber()).thenReturn(CONTRACT_REF);

        ExecuteOrderCommand command =
                new ExecuteOrderCommand(assigned.getId(), TRADER_A, new BigDecimal("3.55000000"));

        MoneyMarketOrder result = subject.execute(command);

        assertThat(result.getStatus().name()).isEqualTo("EXECUTED");
        assertThat(result.getExecutionDetails()).isNotNull();
        assertThat(result.getExecutionDetails().dealingReference()).isEqualTo(DEAL_REF);
        assertThat(result.getExecutionDetails().generatedContractNumber()).isEqualTo(CONTRACT_REF);
        assertThat(result.getExecutionDetails().executionTime()).isEqualTo(FIXED_NOW);
        assertThat(result.getExecutionDetails().counterparty()).isEqualTo("BankCo International");
        assertThat(result.getHandoffStatus()).isEqualTo(com.mmx.order.domain.model.HandoffStatus.PENDING);

        verify(referenceGenerator).generateDealingReference();
        verify(referenceGenerator).generateContractNumber();
        verify(executionHandoffOutbox).schedule(any(MoneyMarketOrder.class));
        verify(auditLogger)
                .log(
                        eq(assigned.getId()),
                        eq(ExecuteOrderService.EVENT_ORDER_EXECUTED),
                        eq(TRADER_A.value()),
                        eq(FIXED_NOW));
    }

    @Test
    void execute_missing_executedRate_rejected() {
        UUID id = UUID.randomUUID();
        ExecuteOrderCommand command = new ExecuteOrderCommand(id, TRADER_A, null);

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(InvalidOrderException.class);

        verifyNoInteractions(referenceGenerator);
        verify(orderRepository, never()).findById(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
        verify(executionHandoffOutbox, never()).schedule(any());
    }

    @Test
    void execute_orderWithoutInstitutionCode_rejected() {
        UUID id = UUID.randomUUID();
        MoneyMarketOrder assigned =
                MoneyMarketOrder.reconstitute(
                        id,
                        new ExternalOrderReference("PM-NO-INST"),
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        new PortfolioNumber("PF-1"),
                        "EUR",
                        new BigDecimal("1000000.00"),
                        TODAY.plusDays(3),
                        new BigDecimal("3.25"),
                        Tenor._3M,
                        null,
                        null,
                        "   ",
                        "BankCo",
                        OrderStatus.ASSIGNED,
                        new Assignment(TRADER_A, FIXED_NOW),
                        null,
                        null,
                        null,
                        FIXED_NOW,
                        FIXED_NOW);
        when(orderRepository.findById(id)).thenReturn(Optional.of(assigned));

        ExecuteOrderCommand command = new ExecuteOrderCommand(id, TRADER_A, new BigDecimal("3.55"));

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(InvalidOrderException.class);

        verify(orderRepository, never()).save(any());
        verify(executionHandoffOutbox, never()).schedule(any());
    }

    @Test
    void execute_empty_catalog_rejected() {
        when(institutionRepository.existsAny()).thenReturn(false);
        ExecuteOrderCommand command =
                new ExecuteOrderCommand(UUID.randomUUID(), TRADER_A, new BigDecimal("3.55"));

        assertThatThrownBy(() -> subject.execute(command))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("No institutions onboarded");

        verify(orderRepository, never()).findById(any());
    }

    @Test
    void execute_unknown_institution_rejected() {
        MoneyMarketOrder assigned = receivedOrderWithInstitution("NOPE-01", "Unknown Bank");
        assigned.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));
        when(institutionRepository.findByInstitutionCode("NOPE-01")).thenReturn(Optional.empty());

        ExecuteOrderCommand command =
                new ExecuteOrderCommand(assigned.getId(), TRADER_A, new BigDecimal("3.55"));

        assertThatThrownBy(() -> subject.execute(command))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void execute_inactive_institution_rejected() {
        when(institutionRepository.findByInstitutionCode("HSBC-01"))
                .thenReturn(Optional.of(new Institution("HSBC-01", "BankCo International", false)));
        MoneyMarketOrder assigned = receivedOrder();
        assigned.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));

        ExecuteOrderCommand command =
                new ExecuteOrderCommand(assigned.getId(), TRADER_A, new BigDecimal("3.55"));

        assertThatThrownBy(() -> subject.execute(command))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void execute_sets_counterparty_from_institution_display_name() {
        MoneyMarketOrder assigned = receivedOrder();
        assigned.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());
        when(referenceGenerator.generateDealingReference()).thenReturn(DEAL_REF);
        when(referenceGenerator.generateContractNumber()).thenReturn(CONTRACT_REF);

        MoneyMarketOrder result =
                subject.execute(
                        new ExecuteOrderCommand(assigned.getId(), TRADER_A, new BigDecimal("3.55")));

        assertThat(result.getExecutionDetails().counterparty()).isEqualTo("BankCo International");
        assertThat(result.getExecutionDetails().institutionCode()).isEqualTo("HSBC-01");
    }

    @Test
    void execute_wrong_trader_throws_unauthorized() {
        MoneyMarketOrder assigned = receivedOrder();
        assigned.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));
        when(referenceGenerator.generateDealingReference()).thenReturn(DEAL_REF);
        when(referenceGenerator.generateContractNumber()).thenReturn(CONTRACT_REF);

        ExecuteOrderCommand command =
                new ExecuteOrderCommand(assigned.getId(), TRADER_B, new BigDecimal("3.55"));

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(UnauthorizedTraderException.class);

        verify(referenceGenerator).generateDealingReference();
        verify(referenceGenerator).generateContractNumber();
        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
        verify(executionHandoffOutbox, never()).schedule(any());
    }

    @Test
    void execute_when_not_assigned_throws_conflict() {
        MoneyMarketOrder received = receivedOrder();
        when(orderRepository.findById(received.getId())).thenReturn(Optional.of(received));
        when(referenceGenerator.generateDealingReference()).thenReturn(DEAL_REF);
        when(referenceGenerator.generateContractNumber()).thenReturn(CONTRACT_REF);

        ExecuteOrderCommand command =
                new ExecuteOrderCommand(received.getId(), TRADER_A, new BigDecimal("3.55"));

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(InvalidStatusTransitionException.class);

        verify(referenceGenerator).generateDealingReference();
        verify(referenceGenerator).generateContractNumber();
        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
        verify(executionHandoffOutbox, never()).schedule(any());
    }

    @Test
    void execute_order_not_found_throws() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        ExecuteOrderCommand command = new ExecuteOrderCommand(id, TRADER_A, new BigDecimal("3.55"));

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(OrderNotFoundException.class);

        verify(executionHandoffOutbox, never()).schedule(any());
    }

    @Test
    void execute_below_pm_minimum_rate_rejected_after_refs_generated() {
        MoneyMarketOrder assigned = receivedOrder();
        assigned.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));
        when(referenceGenerator.generateDealingReference()).thenReturn(DEAL_REF);
        when(referenceGenerator.generateContractNumber()).thenReturn(CONTRACT_REF);

        ExecuteOrderCommand command =
                new ExecuteOrderCommand(assigned.getId(), TRADER_A, new BigDecimal("3.24000000"));

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(InvalidOrderException.class);

        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
        verify(executionHandoffOutbox, never()).schedule(any());
    }

    @Test
    void execute_lifecycle_success_reuses_source_never_calls_contract_generator() {
        MoneyMarketOrder assigned = assignedOnCallIncreaseOrder();
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());
        when(referenceGenerator.generateDealingReference()).thenReturn(DEAL_REF);

        ExecuteOrderCommand command =
                new ExecuteOrderCommand(assigned.getId(), TRADER_A, new BigDecimal("3.55000000"));

        MoneyMarketOrder result = subject.execute(command);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(result.getExecutionDetails().generatedContractNumber()).isEqualTo(LIFECYCLE_SOURCE_REF);
        assertThat(result.getExecutionDetails().dealingReference()).isEqualTo(DEAL_REF);

        verify(referenceGenerator).generateDealingReference();
        verify(referenceGenerator, never()).generateContractNumber();
        verify(executionHandoffOutbox).schedule(any(MoneyMarketOrder.class));
    }

    @Test
    void execute_lifecycle_missing_source_rejected_before_refs_and_save() {
        UUID id = UUID.randomUUID();
        MoneyMarketOrder corrupted =
                MoneyMarketOrder.reconstitute(
                        id,
                        new ExternalOrderReference("PM-BAD-SRC"),
                        OrderType.ON_CALL,
                        OrderOperation.INCREASE,
                        new PortfolioNumber("PF-1"),
                        "EUR",
                        new BigDecimal("1000000.00"),
                        TODAY.plusDays(5),
                        null,
                        null,
                        NoticePeriod._24H,
                        null, "BNKCO", "BankCo",
                        OrderStatus.ASSIGNED,
                        new Assignment(TRADER_A, FIXED_NOW),
                        null,
                        null,
                        null,
                        FIXED_NOW,
                        FIXED_NOW);

        when(orderRepository.findById(id)).thenReturn(Optional.of(corrupted));

        when(institutionRepository.findByInstitutionCode("BNKCO"))
                .thenReturn(Optional.of(new Institution("BNKCO", "BankCo", true)));

        ExecuteOrderCommand command = new ExecuteOrderCommand(id, TRADER_A, new BigDecimal("3.55"));

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(InvalidOrderException.class);

        verify(referenceGenerator, never()).generateDealingReference();
        verify(referenceGenerator, never()).generateContractNumber();
        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
        verify(executionHandoffOutbox, never()).schedule(any());
    }

    @Test
    void execute_when_no_pm_minimum_accepts_rate_below_other_orders_typical_floor() {
        MoneyMarketOrder open = receivedOrderWithoutMinimum();
        open.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(open.getId())).thenReturn(Optional.of(open));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());
        when(referenceGenerator.generateDealingReference()).thenReturn(DEAL_REF);
        when(referenceGenerator.generateContractNumber()).thenReturn(CONTRACT_REF);

        ExecuteOrderCommand command =
                new ExecuteOrderCommand(open.getId(), TRADER_A, new BigDecimal("0.50000000"));

        MoneyMarketOrder result = subject.execute(command);

        assertThat(result.getStatus().name()).isEqualTo("EXECUTED");
        verify(executionHandoffOutbox).schedule(any(MoneyMarketOrder.class));
    }

    private static MoneyMarketOrder receivedOrder() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("PM-EXEC-" + UUID.randomUUID()),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-001"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(3),
                new BigDecimal("3.25000000"),
                Tenor._3M, null, null, "HSBC-01", "BankCo International",
                TODAY);
    }

    private static MoneyMarketOrder receivedOrderWithInstitution(String institutionCode, String counterparty) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("PM-EXEC-" + UUID.randomUUID()),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-001"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(3),
                new BigDecimal("3.25000000"),
                Tenor._3M,
                null,
                null,
                institutionCode,
                counterparty,
                TODAY);
    }

    private static MoneyMarketOrder assignedOnCallIncreaseOrder() {
        MoneyMarketOrder order =
                MoneyMarketOrder.create(
                        new ExternalOrderReference("PM-LIFE-" + UUID.randomUUID()),
                        OrderType.ON_CALL,
                        OrderOperation.INCREASE,
                        new PortfolioNumber("PF-L"),
                        "EUR",
                        new BigDecimal("500000.00"),
                        TODAY.plusDays(5),
                        null,
                        null,
                        NoticePeriod._24H,
                        LIFECYCLE_SOURCE_REF,
                        "HSBC-01",
                        "BankCo International",
                        TODAY);
        order.assign(TRADER_A, FIXED_NOW);
        return order;
    }

    private static MoneyMarketOrder receivedOrderWithoutMinimum() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("PM-EXEC-OPEN-" + UUID.randomUUID()),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-001"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(3),
                null,
                Tenor._3M, null, null, "HSBC-01", "BankCo International",
                TODAY);
    }
}
