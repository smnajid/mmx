package com.mmx.order.config;

import com.mmx.order.MmxApplication;
import com.mmx.order.application.command.CancelOrderCommand;
import com.mmx.order.application.port.in.CancelOrderUseCase;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.service.RoutedOrderOutcomePropagation;
import com.mmx.order.domain.exception.RoutedOrderPairIntegrityException;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = MmxApplication.class)
@ActiveProfiles("rest-test")
class TransactionalOrderLifecycleAtomicityIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final TraderId TRADER = new TraderId("trader-it");

    @Autowired
    CancelOrderUseCase cancelOrderUseCase;

    @Autowired
    OrderRepository orderRepository;

    @Test
    void cancel_hubRoutedPair_propagatesCancelledToClient() {
        RoutedPair pair = persistRoutedPair();

        cancelOrderUseCase.cancel(new CancelOrderCommand(pair.hubId(), TRADER));

        MoneyMarketOrder hub = orderRepository.findById(pair.hubId()).orElseThrow();
        MoneyMarketOrder client = orderRepository.findById(pair.clientId()).orElseThrow();
        assertThat(hub.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(client.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_missingClient_rollsBackHubTerminalTransition() {
        UUID hubId = persistHubOnlyRoutedOrder();

        assertThatThrownBy(() -> cancelOrderUseCase.cancel(new CancelOrderCommand(hubId, TRADER)))
                .isInstanceOf(RoutedOrderPairIntegrityException.class);

        MoneyMarketOrder hub = orderRepository.findById(hubId).orElseThrow();
        assertThat(hub.getStatus()).isEqualTo(OrderStatus.RECEIVED);
    }

    private record RoutedPair(UUID hubId, UUID clientId) {}

    private RoutedPair persistRoutedPair() {
        MoneyMarketOrder client =
                MoneyMarketOrder.create(
                        new ExternalOrderReference("IT-CLIENT-" + UUID.randomUUID()),
                        new LegalEntityCode("PAR"),
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        new PortfolioNumber("PAR-PM-IT"),
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
        client.markRouted(routingId, NOW);
        MoneyMarketOrder savedClient = orderRepository.save(client);

        MoneyMarketOrder hub =
                MoneyMarketOrder.createHubSideFromRouting(
                        new RoutedHubOrderDraft(
                                new LegalEntityCode("LOC"),
                                new PortfolioNumber("PAR-EUR-IT"),
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
                                savedClient.getExternalOrderReference()),
                        TODAY);
        MoneyMarketOrder savedHub = orderRepository.save(hub);
        return new RoutedPair(savedHub.getId(), savedClient.getId());
    }

    private UUID persistHubOnlyRoutedOrder() {
        RoutingId routingId = RoutingId.fromClientOrderId(UUID.randomUUID());
        MoneyMarketOrder hub =
                MoneyMarketOrder.createHubSideFromRouting(
                        new RoutedHubOrderDraft(
                                new LegalEntityCode("LOC"),
                                new PortfolioNumber("PAR-EUR-ORPHAN"),
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
                                new ExternalOrderReference("IT-ORPHAN-" + UUID.randomUUID())),
                        TODAY);
        return orderRepository.save(hub).getId();
    }
}
