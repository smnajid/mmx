package com.mmx.order.application.service;

import com.mmx.order.application.command.ExecuteOrderCommand;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.exception.UnauthorizedTraderException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
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

    @Mock
    OrderRepository orderRepository;

    @Mock
    ReferenceGenerator referenceGenerator;

    @Mock
    AuditLogger auditLogger;

    @Mock
    Clock clock;

    @InjectMocks
    ExecuteOrderService subject;

    @BeforeEach
    void freezeClock() {
        when(clock.now()).thenReturn(FIXED_NOW);
        when(clock.today()).thenReturn(TODAY);
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
                new ExecuteOrderCommand(
                        assigned.getId(),
                        TRADER_A,
                        new BigDecimal("3.55000000"),
                        "BankCo International");

        MoneyMarketOrder result = subject.execute(command);

        assertThat(result.getStatus().name()).isEqualTo("EXECUTED");
        assertThat(result.getExecutionDetails()).isNotNull();
        assertThat(result.getExecutionDetails().dealingReference()).isEqualTo(DEAL_REF);
        assertThat(result.getExecutionDetails().generatedContractNumber()).isEqualTo(CONTRACT_REF);
        assertThat(result.getExecutionDetails().executionTime()).isEqualTo(FIXED_NOW);

        verify(referenceGenerator).generateDealingReference();
        verify(referenceGenerator).generateContractNumber();
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
        ExecuteOrderCommand command = new ExecuteOrderCommand(id, TRADER_A, null, "BankCo");

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(InvalidOrderException.class);

        verifyNoInteractions(referenceGenerator);
        verify(orderRepository, never()).findById(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    @Test
    void execute_blank_counterparty_rejected() {
        UUID id = UUID.randomUUID();
        ExecuteOrderCommand command =
                new ExecuteOrderCommand(id, TRADER_A, new BigDecimal("3.55"), "   ");

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(InvalidOrderException.class);

        verifyNoInteractions(referenceGenerator);
        verify(orderRepository, never()).findById(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    @Test
    void execute_wrong_trader_throws_unauthorized() {
        MoneyMarketOrder assigned = receivedOrder();
        assigned.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));
        when(referenceGenerator.generateDealingReference()).thenReturn(DEAL_REF);
        when(referenceGenerator.generateContractNumber()).thenReturn(CONTRACT_REF);

        ExecuteOrderCommand command =
                new ExecuteOrderCommand(
                        assigned.getId(), TRADER_B, new BigDecimal("3.55"), "BankCo International");

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(UnauthorizedTraderException.class);

        verify(referenceGenerator).generateDealingReference();
        verify(referenceGenerator).generateContractNumber();
        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    @Test
    void execute_when_not_assigned_throws_conflict() {
        MoneyMarketOrder received = receivedOrder();
        when(orderRepository.findById(received.getId())).thenReturn(Optional.of(received));
        when(referenceGenerator.generateDealingReference()).thenReturn(DEAL_REF);
        when(referenceGenerator.generateContractNumber()).thenReturn(CONTRACT_REF);

        ExecuteOrderCommand command =
                new ExecuteOrderCommand(
                        received.getId(), TRADER_A, new BigDecimal("3.55"), "BankCo International");

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(InvalidStatusTransitionException.class);

        verify(referenceGenerator).generateDealingReference();
        verify(referenceGenerator).generateContractNumber();
        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    @Test
    void execute_order_not_found_throws() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        ExecuteOrderCommand command =
                new ExecuteOrderCommand(id, TRADER_A, new BigDecimal("3.55"), "BankCo International");

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(OrderNotFoundException.class);
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
                Tenor._3M,
                null,
                null,
                null,
                TODAY);
    }
}
