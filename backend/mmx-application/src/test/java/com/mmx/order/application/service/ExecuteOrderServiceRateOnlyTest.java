package com.mmx.order.application.service;

import com.mmx.order.application.command.ExecuteOrderCommand;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.ExecutionHandoffOutbox;
import com.mmx.order.domain.model.HubLocality;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.application.port.out.RoutedPairLocalityResolver;
import com.mmx.order.application.port.out.RoutingOutcomeOutbox;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TraderId;
import com.mmx.order.domain.policy.OrderAgainstInstitutionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@Tag("fast")

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecuteOrderServiceRateOnlyTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final TraderId TRADER_A = new TraderId("trader-a");
    private static final DealingReference DEAL_REF = new DealingReference("DL-rate-only");
    private static final ContractNumber CONTRACT_REF = new ContractNumber("CN-rate-only");

    private static final Institution HSBC =
            new Institution("HSBC-01", "BankCo International", true);

    @Mock
    OrderRepository orderRepository;

    @Mock
    InstitutionRepository institutionRepository;

    @Mock
    ReferenceGenerator referenceGenerator;

    @Mock
    AuditLogger auditLogger;

    @Mock
    Clock clock;

    @Mock
    ExecutionHandoffOutbox executionHandoffOutbox;

    ExecuteOrderService subject;

    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(FIXED_NOW);
        when(clock.today()).thenReturn(TODAY);
        when(institutionRepository.existsAny()).thenReturn(true);
        when(institutionRepository.findByInstitutionCode("HSBC-01")).thenReturn(Optional.of(HSBC));
        when(referenceGenerator.generateDealingReference()).thenReturn(DEAL_REF);
        when(referenceGenerator.generateContractNumber()).thenReturn(CONTRACT_REF);
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());
        subject =
                new ExecuteOrderService(
                        orderRepository,
                        institutionRepository,
                        new OrderAgainstInstitutionPolicy(),
                        referenceGenerator,
                        auditLogger,
                        clock,
                        executionHandoffOutbox,
                        new RoutedOrderOutcomePropagationService(orderRepository, referenceGenerator),
                        org.mockito.Mockito.mock(RoutingOutcomeOutbox.class),
                        code -> HubLocality.LOCAL);
    }

    @Test
    void execute_usesInstitutionAlreadyOnOrder_notFromRequest() {
        MoneyMarketOrder assigned = orderWithInstitution("HSBC-01", "BankCo International");
        assigned.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));

        MoneyMarketOrder result =
                subject.execute(
                        new ExecuteOrderCommand(
                                assigned.getId(), TRADER_A, new BigDecimal("3.55000000")));

        assertThat(result.getExecutionDetails().institutionCode()).isEqualTo("HSBC-01");
        assertThat(result.getExecutionDetails().counterparty()).isEqualTo("BankCo International");
        verify(institutionRepository).findByInstitutionCode("HSBC-01");
    }

    @Test
    void execute_rejectsWhenInstitutionBecameInactiveSinceIntake() {
        when(institutionRepository.findByInstitutionCode("HSBC-01"))
                .thenReturn(Optional.of(new Institution("HSBC-01", "BankCo International", false)));
        MoneyMarketOrder assigned = orderWithInstitution("HSBC-01", "BankCo International");
        assigned.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));

        assertThatThrownBy(
                        () ->
                                subject.execute(
                                        new ExecuteOrderCommand(
                                                assigned.getId(), TRADER_A, new BigDecimal("3.55"))))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("not active");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void execute_acceptsRateOnlyCommand() {
        MoneyMarketOrder assigned = orderWithInstitution("HSBC-01", "BankCo International");
        assigned.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));

        ExecuteOrderCommand command =
                new ExecuteOrderCommand(assigned.getId(), TRADER_A, new BigDecimal("3.55000000"));

        MoneyMarketOrder result = subject.execute(command);

        assertThat(result.getStatus().name()).isEqualTo("EXECUTED");
        assertThat(result.getExecutionDetails().executedRate())
                .isEqualByComparingTo(new BigDecimal("3.55000000"));
    }

    private static MoneyMarketOrder orderWithInstitution(String institutionCode, String counterparty) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("PM-RATE-" + UUID.randomUUID()),
                new LegalEntityCode("LOC"),
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
}
