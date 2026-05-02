package com.mmx.order.application.service;

import com.mmx.order.application.command.AssignOrderCommand;
import com.mmx.order.application.command.UnassignOrderCommand;
import com.mmx.order.application.port.in.OrderPage;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.exception.UnauthorizedTraderException;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TraderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssignmentServiceTest {

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

    @InjectMocks
    AssignmentService subject;

    @BeforeEach
    void freezeClock() {
        when(clock.now()).thenReturn(FIXED_NOW);
        when(clock.today()).thenReturn(TODAY);
    }

    @Test
    void assign_from_received_persists_and_audits() {
        MoneyMarketOrder received = newReceivedOrder();
        when(orderRepository.findById(received.getId())).thenReturn(Optional.of(received));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        MoneyMarketOrder result = subject.assign(new AssignOrderCommand(received.getId(), TRADER_A));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(result.getAssignment()).isNotNull();
        assertThat(result.getAssignment().traderId()).isEqualTo(TRADER_A);

        verify(auditLogger)
                .log(eq(result.getId()), eq(AssignmentService.EVENT_ORDER_ASSIGNED), eq(TRADER_A.value()), eq(FIXED_NOW));
    }

    @Test
    void assign_when_not_found_throws() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.assign(new AssignOrderCommand(id, TRADER_A)))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void assign_when_already_assigned_throws_conflict() {
        MoneyMarketOrder order = newReceivedOrder();
        order.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> subject.assign(new AssignOrderCommand(order.getId(), TRADER_B)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void unassign_from_assigned_persists_and_audits() {
        MoneyMarketOrder order = newReceivedOrder();
        order.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        MoneyMarketOrder result = subject.unassign(new UnassignOrderCommand(order.getId(), TRADER_A));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(result.getAssignment()).isNull();

        verify(auditLogger)
                .log(eq(result.getId()), eq(AssignmentService.EVENT_ORDER_UNASSIGNED), eq(TRADER_A.value()), eq(FIXED_NOW));
    }

    @Test
    void unassign_by_wrong_trader_throws_unauthorized() {
        MoneyMarketOrder order = newReceivedOrder();
        order.assign(TRADER_A, FIXED_NOW);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> subject.unassign(new UnassignOrderCommand(order.getId(), TRADER_B)))
                .isInstanceOf(UnauthorizedTraderException.class);
    }

    @Test
    void listAssignedOrders_returns_only_matching_trader() {
        MoneyMarketOrder forA = newReceivedOrder();
        forA.assign(TRADER_A, FIXED_NOW);

        when(orderRepository.findByAssignedTraderIdAndStatus(TRADER_A, OrderStatus.ASSIGNED))
                .thenReturn(List.of(forA));

        OrderPage page = subject.listAssignedOrders(TRADER_A, 0, 20);

        assertThat(page.content()).containsExactly(forA);
        assertThat(page.totalElements()).isEqualTo(1);

        ArgumentCaptor<TraderId> traderCaptor = ArgumentCaptor.forClass(TraderId.class);
        verify(orderRepository).findByAssignedTraderIdAndStatus(traderCaptor.capture(), eq(OrderStatus.ASSIGNED));
        assertThat(traderCaptor.getValue()).isEqualTo(TRADER_A);
    }

    private static MoneyMarketOrder newReceivedOrder() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("PM-ASGN-" + UUID.randomUUID()),
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
