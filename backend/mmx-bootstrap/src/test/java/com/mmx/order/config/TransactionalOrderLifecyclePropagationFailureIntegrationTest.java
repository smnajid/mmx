package com.mmx.order.config;

import com.mmx.order.MmxApplication;
import com.mmx.order.application.command.CancelOrderCommand;
import com.mmx.order.application.port.in.CancelOrderUseCase;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.service.RoutedOrderOutcomePropagation;
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
import org.junit.jupiter.api.Tag;
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
@Tag("integration")

@SpringBootTest(classes = MmxApplication.class)
@ActiveProfiles("rest-test")
@Import(TransactionalOrderLifecyclePropagationFailureIntegrationTest.FailingPropagationConfig.class)
class TransactionalOrderLifecyclePropagationFailureIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final TraderId TRADER = new TraderId("trader-it");

    @Autowired
    CancelOrderUseCase cancelOrderUseCase;

    @Autowired
    OrderRepository orderRepository;

    @Test
    void cancel_propagationFailure_rollsBackHubTerminalTransition() {
        RoutedPair pair = persistRoutedPair();

        assertThatThrownBy(() -> cancelOrderUseCase.cancel(new CancelOrderCommand(pair.hubId(), TRADER)))
                .isInstanceOf(SimulatedPropagationFailureException.class);

        MoneyMarketOrder hub = orderRepository.findById(pair.hubId()).orElseThrow();
        MoneyMarketOrder client = orderRepository.findById(pair.clientId()).orElseThrow();
        assertThat(hub.getStatus()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(client.getStatus()).isEqualTo(OrderStatus.ROUTED);
    }

    private record RoutedPair(UUID hubId, UUID clientId) {}

    private RoutedPair persistRoutedPair() {
        MoneyMarketOrder client =
                MoneyMarketOrder.create(
                        new ExternalOrderReference("IT-CLIENT-FAIL-" + UUID.randomUUID()),
                        new LegalEntityCode("PAR"),
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        new PortfolioNumber("PAR-PM-FAIL"),
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
                                new PortfolioNumber("PAR-EUR-FAIL"),
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

    static final class SimulatedPropagationFailureException extends RuntimeException {
        SimulatedPropagationFailureException() {
            super("simulated propagation failure");
        }
    }

    @TestConfiguration
    static class FailingPropagationConfig {
        @Bean
        @Primary
        RoutedOrderOutcomePropagation failingPropagation(RoutedOrderOutcomePropagation delegate) {
            return new RoutedOrderOutcomePropagation() {
                @Override
                public com.mmx.order.application.service.ExecutionPropagationResult propagateExecution(
                        MoneyMarketOrder hubOrder) {
                    return delegate.propagateExecution(hubOrder);
                }

                @Override
                public void propagateCancel(MoneyMarketOrder hubOrder, Instant now) {
                    throw new SimulatedPropagationFailureException();
                }

                @Override
                public void propagateReject(MoneyMarketOrder hubOrder, String reason, Instant now) {
                    throw new SimulatedPropagationFailureException();
                }
            };
        }
    }
}
