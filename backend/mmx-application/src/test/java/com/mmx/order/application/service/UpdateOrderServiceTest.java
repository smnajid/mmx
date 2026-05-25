package com.mmx.order.application.service;

import com.mmx.order.application.command.UpdateOrderCommand;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OpenPositionPort;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.exception.UnauthorizedTraderException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateOrderServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final TraderId TRADER_A = new TraderId("trader-a");
    private static final TraderId TRADER_B = new TraderId("trader-b");

    @Mock
    OrderRepository orderRepository;

    @Mock
    AuditLogger auditLogger;

    @Mock
    Clock clock;

    @Mock
    ManagedCurrencyRepository managedCurrencyRepository;

    @Mock
    OpenPositionPort openPositionPort;

    UpdateOrderService subject;

    @BeforeEach
    void freezeClock() {
        when(clock.now()).thenReturn(FIXED_NOW);
        when(clock.today()).thenReturn(TODAY);
        subject =
                new UpdateOrderService(
                        orderRepository, managedCurrencyRepository, openPositionPort, auditLogger, clock);
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(permissiveEur()));
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
    void update_amount_success_audits() {
        MoneyMarketOrder assigned = assignedOrder();
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        UpdateOrderCommand command =
                new UpdateOrderCommand(
                        assigned.getId(), TRADER_A, new BigDecimal("6000000.00"), null);

        MoneyMarketOrder result = subject.update(command);

        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("6000000.00"));

        verify(auditLogger)
                .log(
                        eq(assigned.getId()),
                        eq(UpdateOrderService.EVENT_ORDER_UPDATED),
                        eq(TRADER_A.value()),
                        eq(FIXED_NOW));
    }

    @Test
    void update_value_date_too_soon_rejected() {
        MoneyMarketOrder assigned = assignedOrder();
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));

        UpdateOrderCommand command =
                new UpdateOrderCommand(
                        assigned.getId(), TRADER_A, null, TODAY.plusDays(1));

        assertThatThrownBy(() -> subject.update(command)).isInstanceOf(InvalidOrderException.class);

        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    @Test
    void update_by_wrong_trader_throws() {
        MoneyMarketOrder assigned = assignedOrder();
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));

        UpdateOrderCommand command =
                new UpdateOrderCommand(assigned.getId(), TRADER_B, new BigDecimal("6000000.00"), null);

        assertThatThrownBy(() -> subject.update(command)).isInstanceOf(UnauthorizedTraderException.class);

        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    @Test
    void update_when_not_assigned_throws_conflict() {
        MoneyMarketOrder received = receivedOrder();
        when(orderRepository.findById(received.getId())).thenReturn(Optional.of(received));

        UpdateOrderCommand command =
                new UpdateOrderCommand(received.getId(), TRADER_A, new BigDecimal("6000000.00"), null);

        assertThatThrownBy(() -> subject.update(command)).isInstanceOf(InvalidStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    @Test
    void update_no_fields_throws() {
        MoneyMarketOrder assigned = assignedOrder();

        UpdateOrderCommand command =
                new UpdateOrderCommand(assigned.getId(), TRADER_A, null, null);

        assertThatThrownBy(() -> subject.update(command)).isInstanceOf(InvalidOrderException.class);

        verify(orderRepository, never()).findById(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    @Test
    void update_order_not_found_throws() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        UpdateOrderCommand command =
                new UpdateOrderCommand(id, TRADER_A, new BigDecimal("6000000.00"), null);

        assertThatThrownBy(() -> subject.update(command)).isInstanceOf(OrderNotFoundException.class);
    }

    private static MoneyMarketOrder receivedOrder() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("PM-UPD-" + UUID.randomUUID()),
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

    private static MoneyMarketOrder assignedOrder() {
        MoneyMarketOrder order = receivedOrder();
        order.assign(TRADER_A, FIXED_NOW);
        return order;
    }
}
