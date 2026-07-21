package com.mmx.order.application.service;

import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.domain.exception.RoutedOrderPairIntegrityException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.NoticePeriod;
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

@ExtendWith(MockitoExtension.class)
class RoutedOrderOutcomePropagationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final TraderId TRADER = new TraderId("trader-a");
    private static final ContractNumber HUB_CONTRACT = new ContractNumber("CN-hub");
    private static final ContractNumber CLIENT_CONTRACT = new ContractNumber("CN-client-new");
    private static final ContractNumber LIFECYCLE_SOURCE = new ContractNumber("CN-lifecycle-src");

    @Mock
    OrderRepository orderRepository;

    @Mock
    ReferenceGenerator referenceGenerator;

    RoutedOrderOutcomePropagationService subject;

    @BeforeEach
    void setUp() {
        subject = new RoutedOrderOutcomePropagationService(orderRepository, referenceGenerator);
    }

    @Test
    void propagateExecution_subscription_propagatesExecutedWithNewClientContractNumber() {
        RoutedPair pair = executedSubscriptionPair();
        when(orderRepository.findRoutedClientOrderByRoutingId(pair.routingId()))
                .thenReturn(Optional.of(pair.client()));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());
        when(referenceGenerator.generateContractNumber()).thenReturn(CLIENT_CONTRACT);

        ExecutionPropagationResult result = subject.propagateExecution(pair.hub());

        assertThat(result.clientOrder().getStatus()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(result.clientOrder().getExecutionDetails().executedRate())
                .isEqualByComparingTo(new BigDecimal("3.55"));
        assertThat(result.clientOrder().getExecutionDetails().dealingReference())
                .isEqualTo(new DealingReference("DL-hub"));
        assertThat(result.clientOrder().getExecutionDetails().generatedContractNumber())
                .isEqualTo(CLIENT_CONTRACT);
        assertThat(result.handoffContext().routingId()).isEqualTo(pair.routingId());
        assertThat(result.handoffContext().originatingLegalEntityCode()).isEqualTo(pair.client().getLegalEntityCode());
        assertThat(result.handoffContext().clientOrderId()).isEqualTo(pair.client().getId());
        assertThat(result.handoffContext().clientPortfolioNumber())
                .isEqualTo(pair.client().getPortfolioNumber().value());
        assertThat(result.handoffContext().clientCounterparty()).isEqualTo("BNP via LOC");
    }

    @Test
    void propagateExecution_lifecycle_reusesClientSourceContractNumber() {
        RoutedPair pair = executedLifecyclePair();
        when(orderRepository.findRoutedClientOrderByRoutingId(pair.routingId()))
                .thenReturn(Optional.of(pair.client()));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        ExecutionPropagationResult result = subject.propagateExecution(pair.hub());

        assertThat(result.clientOrder().getExecutionDetails().generatedContractNumber())
                .isEqualTo(LIFECYCLE_SOURCE);
        verify(referenceGenerator, never()).generateContractNumber();
    }

    @Test
    void propagateCancel_propagatesCancelledToClient() {
        RoutedPair pair = routedPair();
        when(orderRepository.findRoutedClientOrderByRoutingId(pair.routingId()))
                .thenReturn(Optional.of(pair.client()));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        pair.hub().cancel(FIXED_NOW);
        subject.propagateCancel(pair.hub(), FIXED_NOW);

        verify(orderRepository).save(any(MoneyMarketOrder.class));
        assertThat(pair.client().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void propagateReject_propagatesRejectedToClientWithReason() {
        RoutedPair pair = routedPair();
        when(orderRepository.findRoutedClientOrderByRoutingId(pair.routingId()))
                .thenReturn(Optional.of(pair.client()));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        pair.hub().reject(TRADER, "No capacity", FIXED_NOW);
        subject.propagateReject(pair.hub(), "No capacity", FIXED_NOW);

        verify(orderRepository).save(any(MoneyMarketOrder.class));
        assertThat(pair.client().getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(pair.client().getRejectionReason()).isEqualTo("No capacity");
    }

    @Test
    void propagateExecution_missingClient_throwsPairIntegrityException() {
        RoutedPair pair = executedSubscriptionPair();
        when(orderRepository.findRoutedClientOrderByRoutingId(pair.routingId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.propagateExecution(pair.hub()))
                .isInstanceOf(RoutedOrderPairIntegrityException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void propagateCancel_missingClient_throwsPairIntegrityException() {
        RoutedPair pair = routedPair();
        when(orderRepository.findRoutedClientOrderByRoutingId(pair.routingId())).thenReturn(Optional.empty());
        pair.hub().cancel(FIXED_NOW);

        assertThatThrownBy(() -> subject.propagateCancel(pair.hub(), FIXED_NOW))
                .isInstanceOf(RoutedOrderPairIntegrityException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void propagateReject_missingClient_throwsPairIntegrityException() {
        RoutedPair pair = routedPair();
        when(orderRepository.findRoutedClientOrderByRoutingId(pair.routingId())).thenReturn(Optional.empty());
        pair.hub().reject(TRADER, "Declined", FIXED_NOW);

        assertThatThrownBy(() -> subject.propagateReject(pair.hub(), "Declined", FIXED_NOW))
                .isInstanceOf(RoutedOrderPairIntegrityException.class);
        verify(orderRepository, never()).save(any());
    }

    private record RoutedPair(MoneyMarketOrder hub, MoneyMarketOrder client, RoutingId routingId) {}

    private static RoutedPair routedPair() {
        MoneyMarketOrder client = clientOrder();
        RoutingId routingId = RoutingId.fromClientOrderId(client.getId());
        client.markRouted(routingId, FIXED_NOW);
        MoneyMarketOrder hub = hubOrder(routingId, client, OrderOperation.SUBSCRIPTION, null);
        return new RoutedPair(hub, client, routingId);
    }

    private static RoutedPair executedSubscriptionPair() {
        RoutedPair pair = routedPair();
        pair.hub().assign(TRADER, FIXED_NOW);
        pair.hub()
                .execute(
                        new BigDecimal("3.55"),
                        "BankCo International",
                        "HSBC-01",
                        new DealingReference("DL-hub"),
                        HUB_CONTRACT,
                        TRADER,
                        FIXED_NOW);
        return pair;
    }

    private static RoutedPair executedLifecyclePair() {
        MoneyMarketOrder client =
                MoneyMarketOrder.create(
                        new ExternalOrderReference("PM-CLIENT-LC-" + UUID.randomUUID()),
                        new LegalEntityCode("PAR"),
                        OrderType.ON_CALL,
                        OrderOperation.INCREASE,
                        new PortfolioNumber("PAR-PM-L"),
                        "EUR",
                        new BigDecimal("500000.00"),
                        TODAY.plusDays(5),
                        null,
                        null,
                        NoticePeriod._24H,
                        LIFECYCLE_SOURCE,
                        "BNPLOC",
                        "BNP via LOC",
                        TODAY);
        RoutingId routingId = RoutingId.fromClientOrderId(client.getId());
        client.markRouted(routingId, FIXED_NOW);
        MoneyMarketOrder hub = hubOrder(routingId, client, OrderOperation.INCREASE, LIFECYCLE_SOURCE);
        hub.assign(TRADER, FIXED_NOW);
        hub.execute(
                new BigDecimal("3.55"),
                "BankCo International",
                "HSBC-01",
                new DealingReference("DL-hub"),
                LIFECYCLE_SOURCE,
                TRADER,
                FIXED_NOW);
        return new RoutedPair(hub, client, routingId);
    }

    private static MoneyMarketOrder clientOrder() {
        return MoneyMarketOrder.create(
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
    }

    private static MoneyMarketOrder hubOrder(
            RoutingId routingId, MoneyMarketOrder client, OrderOperation operation, ContractNumber source) {
        return MoneyMarketOrder.createHubSideFromRouting(
                new RoutedHubOrderDraft(
                        new LegalEntityCode("LOC"),
                        new PortfolioNumber("PAR-EUR-001"),
                        "HSBC-01",
                        "BankCo International",
                        "EUR",
                        new BigDecimal("1000000.00"),
                        TODAY.plusDays(5),
                        client.getOrderType(),
                        operation,
                        client.getTenor(),
                        client.getNoticePeriod(),
                        new BigDecimal("3.25"),
                        source,
                        routingId,
                        new LegalEntityCode("PAR"),
                        client.getExternalOrderReference()),
                TODAY);
    }
}
