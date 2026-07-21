package com.mmx.order.application.service;

import com.mmx.order.application.command.CancelOrderCommand;
import com.mmx.order.application.command.RejectOrderCommand;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.exception.RoutedOrderPairIntegrityException;
import com.mmx.order.domain.exception.UnauthorizedTraderException;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutedHubOrderDraft;
import com.mmx.order.domain.model.RoutingId;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderLifecycleServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final TraderId TRADER = new TraderId("trader-a");
    private static final TraderId OTHER_TRADER = new TraderId("trader-b");

    @Mock
    OrderRepository orderRepository;

    @Mock
    AuditLogger auditLogger;

    @Mock
    Clock clock;

    @Mock
    ReferenceGenerator referenceGenerator;

    OrderLifecycleService subject;

    @BeforeEach
    void freezeClock() {
        when(clock.now()).thenReturn(FIXED_NOW);
        when(clock.today()).thenReturn(TODAY);
        subject =
                new OrderLifecycleService(
                        orderRepository,
                        auditLogger,
                        clock,
                        new RoutedOrderOutcomePropagationService(orderRepository, referenceGenerator));
    }

    @Test
    void cancel_from_received_succeeds_and_audits() {
        MoneyMarketOrder received = receivedOrder();
        when(orderRepository.findById(received.getId())).thenReturn(Optional.of(received));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        MoneyMarketOrder result =
                subject.cancel(new CancelOrderCommand(received.getId(), TRADER));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(auditLogger)
                .log(
                        eq(received.getId()),
                        eq(OrderLifecycleService.EVENT_ORDER_CANCELLED),
                        eq(TRADER.value()),
                        eq(FIXED_NOW));
    }

    @Test
    void reject_from_received_with_reason_succeeds_and_audits() {
        MoneyMarketOrder received = receivedOrder();
        when(orderRepository.findById(received.getId())).thenReturn(Optional.of(received));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        MoneyMarketOrder result =
                subject.reject(new RejectOrderCommand(received.getId(), "Insufficient liquidity", TRADER));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("Insufficient liquidity");
        verify(auditLogger)
                .log(
                        eq(received.getId()),
                        eq(OrderLifecycleService.EVENT_ORDER_REJECTED),
                        eq(TRADER.value()),
                        eq(FIXED_NOW));
    }

    @Test
    void cancel_when_not_received_throws_conflict() {
        MoneyMarketOrder assigned = receivedOrder();
        assigned.assign(TRADER, FIXED_NOW);
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));

        assertThatThrownBy(() -> subject.cancel(new CancelOrderCommand(assigned.getId(), TRADER)))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    @Test
    void reject_from_assigned_succeeds_for_assignee() {
        MoneyMarketOrder assigned = receivedOrder();
        assigned.assign(TRADER, FIXED_NOW);
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        MoneyMarketOrder result =
                subject.reject(new RejectOrderCommand(assigned.getId(), "No capacity", TRADER));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.REJECTED);
        verify(auditLogger)
                .log(
                        eq(assigned.getId()),
                        eq(OrderLifecycleService.EVENT_ORDER_REJECTED),
                        eq(TRADER.value()),
                        eq(FIXED_NOW));
    }

    @Test
    void reject_from_assigned_non_assignee_throws_forbidden() {
        MoneyMarketOrder assigned = receivedOrder();
        assigned.assign(TRADER, FIXED_NOW);
        when(orderRepository.findById(assigned.getId())).thenReturn(Optional.of(assigned));

        assertThatThrownBy(
                        () ->
                                subject.reject(
                                        new RejectOrderCommand(assigned.getId(), "Intruder rejects", OTHER_TRADER)))
                .isInstanceOf(UnauthorizedTraderException.class);

        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    @Test
    void reject_without_reason_throws_validation_error() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> subject.reject(new RejectOrderCommand(id, "", TRADER)))
                .isInstanceOf(InvalidOrderException.class);

        assertThatThrownBy(() -> subject.reject(new RejectOrderCommand(id, "   ", TRADER)))
                .isInstanceOf(InvalidOrderException.class);

        assertThatThrownBy(() -> subject.reject(new RejectOrderCommand(id, null, TRADER)))
                .isInstanceOf(InvalidOrderException.class);

        verify(orderRepository, never()).findById(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    @Test
    void cancel_order_not_found_throws() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.cancel(new CancelOrderCommand(id, TRADER)))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void cancel_hubRoutedOrder_propagatesCancelledToClient() {
        RoutedPair pair = routedPair();
        when(orderRepository.findById(pair.hub().getId())).thenReturn(Optional.of(pair.hub()));
        when(orderRepository.findRoutedClientOrderByRoutingId(pair.routingId()))
                .thenReturn(Optional.of(pair.client()));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        MoneyMarketOrder result =
                subject.cancel(new CancelOrderCommand(pair.hub().getId(), TRADER));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        ArgumentCaptor<MoneyMarketOrder> saved = ArgumentCaptor.forClass(MoneyMarketOrder.class);
        verify(orderRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(saved.getAllValues().get(1).getId()).isEqualTo(pair.client().getId());
    }

    @Test
    void reject_hubRoutedOrder_propagatesRejectedToClient() {
        RoutedPair pair = routedPair();
        when(orderRepository.findById(pair.hub().getId())).thenReturn(Optional.of(pair.hub()));
        when(orderRepository.findRoutedClientOrderByRoutingId(pair.routingId()))
                .thenReturn(Optional.of(pair.client()));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        MoneyMarketOrder result =
                subject.reject(new RejectOrderCommand(pair.hub().getId(), "No capacity", TRADER));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.REJECTED);
        ArgumentCaptor<MoneyMarketOrder> saved = ArgumentCaptor.forClass(MoneyMarketOrder.class);
        verify(orderRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(saved.getAllValues().get(1).getRejectionReason()).isEqualTo("No capacity");
    }

    @Test
    void cancel_hubRoutedOrder_missingClient_throwsPairIntegrityException() {
        RoutedPair pair = routedPair();
        when(orderRepository.findById(pair.hub().getId())).thenReturn(Optional.of(pair.hub()));
        when(orderRepository.findRoutedClientOrderByRoutingId(pair.routingId())).thenReturn(Optional.empty());
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        assertThatThrownBy(() -> subject.cancel(new CancelOrderCommand(pair.hub().getId(), TRADER)))
                .isInstanceOf(RoutedOrderPairIntegrityException.class);
        verify(orderRepository, times(1)).save(any(MoneyMarketOrder.class));
    }

    @Test
    void reject_hubRoutedOrder_missingClient_throwsPairIntegrityException() {
        RoutedPair pair = routedPair();
        when(orderRepository.findById(pair.hub().getId())).thenReturn(Optional.of(pair.hub()));
        when(orderRepository.findRoutedClientOrderByRoutingId(pair.routingId())).thenReturn(Optional.empty());
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        assertThatThrownBy(
                        () -> subject.reject(new RejectOrderCommand(pair.hub().getId(), "Declined", TRADER)))
                .isInstanceOf(RoutedOrderPairIntegrityException.class);
        verify(orderRepository, times(1)).save(any(MoneyMarketOrder.class));
    }

    private record RoutedPair(MoneyMarketOrder hub, MoneyMarketOrder client, RoutingId routingId) {}

    private static RoutedPair routedPair() {
        MoneyMarketOrder client =
                MoneyMarketOrder.create(
                        new ExternalOrderReference("PM-CLIENT-" + UUID.randomUUID()),
                        new LegalEntityCode("PAR"),
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        new PortfolioNumber("PAR-PM-77"),
                        "EUR",
                        new BigDecimal("1000000.00"),
                        TODAY.plusDays(5),
                        new BigDecimal("3.25"),
                        Tenor._3M,
                        null,
                        null,
                        "BNPLOC",
                        "BNP via LOC",
                        TODAY);
        RoutingId routingId = RoutingId.fromClientOrderId(client.getId());
        client.markRouted(routingId, FIXED_NOW);
        MoneyMarketOrder hub =
                MoneyMarketOrder.createHubSideFromRouting(
                        new RoutedHubOrderDraft(
                                new LegalEntityCode("LOC"),
                                new PortfolioNumber("PAR-EUR-001"),
                                "HSBC-01",
                                "BankCo International",
                                "EUR",
                                new BigDecimal("1000000.00"),
                                TODAY.plusDays(5),
                                OrderType.TERM,
                                OrderOperation.SUBSCRIPTION,
                                Tenor._3M,
                                null,
                                new BigDecimal("3.25"),
                                null,
                                routingId,
                                new LegalEntityCode("PAR"),
                                client.getExternalOrderReference()),
                        TODAY);
        return new RoutedPair(hub, client, routingId);
    }

    private static MoneyMarketOrder receivedOrder() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("PM-LIFE-" + UUID.randomUUID()),
                new LegalEntityCode("LOC"),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-001"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(3),
                new BigDecimal("3.25000000"),
                Tenor._3M, null, null, "BNKCO", "BankCo",
                TODAY);
    }
}
