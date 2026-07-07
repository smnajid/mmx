package com.mmx.order.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.application.port.out.ExecutionHandoffRoutingContext;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.TraderId;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OrderExecutedV1PayloadMapperTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 10);

    private final OrderExecutedV1PayloadMapper mapper = new OrderExecutedV1PayloadMapper();

    @Test
    void builds_contract_aligned_payload_json() throws Exception {
        MoneyMarketOrder order = subscribedAssignedExecuted();

        String json = mapper.toJsonPayload(order);
        JsonNode node = new ObjectMapper().readTree(json);

        assertThat(node.path("eventType").asText()).isEqualTo("OrderExecutedV1");
        assertThat(node.path("orderId").asText()).isEqualTo(order.getId().toString());
        assertThat(node.path("orderType").asText()).isEqualTo("TERM");
        assertThat(node.path("orderOperation").asText()).isEqualTo("SUBSCRIPTION");
        assertThat(node.path("portfolioNumber").asText()).isEqualTo("PF-001");
        assertThat(node.path("currency").asText()).isEqualTo("EUR");
        assertThat(node.path("counterparty").asText()).isEqualTo("BankCo International");
        assertThat(node.path("dealingReference").asText()).isEqualTo("DL-001");
        assertThat(node.path("contractNumber").asText()).isEqualTo("CN-NEW");
        assertThat(node.path("externalOrderReference").asText()).isEqualTo(order.getExternalOrderReference().value());
        assertThat(node.path("tenor").asText()).isEqualTo("3M");
    }

    @Test
    void native_order_omits_routing_context() throws Exception {
        MoneyMarketOrder order = subscribedAssignedExecuted();
        JsonNode node = new ObjectMapper().readTree(mapper.toJsonPayload(order));
        assertThat(node.has("routingId")).isFalse();
        assertThat(node.has("clientOrderId")).isFalse();
    }

    @Test
    void routed_hub_order_includes_routing_context() throws Exception {
        MoneyMarketOrder hub = hubSideRoutedExecuted();
        RoutingId routingId = hub.getRoutingId();
        ExecutionHandoffRoutingContext ctx =
                new ExecutionHandoffRoutingContext(
                        routingId,
                        new LegalEntityCode("PAR"),
                        java.util.UUID.randomUUID(),
                        "PAR-PM-77",
                        "BNP via LOC");
        JsonNode node = new ObjectMapper().readTree(mapper.toJsonPayload(hub, ctx));
        assertThat(node.path("routingId").asText()).isEqualTo(routingId.value().toString());
        assertThat(node.path("originatingLegalEntityCode").asText()).isEqualTo("PAR");
        assertThat(node.path("clientPortfolioNumber").asText()).isEqualTo("PAR-PM-77");
        assertThat(node.path("clientCounterparty").asText()).isEqualTo("BNP via LOC");
    }

    private static MoneyMarketOrder hubSideRoutedExecuted() {
        MoneyMarketOrder client =
                MoneyMarketOrder.create(
                        new ExternalOrderReference("PM-CLIENT-" + Instant.now().toEpochMilli()),
                        new LegalEntityCode("PAR"),
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        new PortfolioNumber("PAR-PM-77"),
                        "EUR",
                        new BigDecimal("1000000.00"),
                        TODAY.plusDays(5),
                        new BigDecimal("3.25"),
                        Tenor._3M, null, null, "BNPLOC", "BNP via LOC",
                        TODAY);
        RoutingId routingId = RoutingId.fromClientOrderId(client.getId());
        MoneyMarketOrder hub =
                MoneyMarketOrder.createHubSideFromRouting(
                        new com.mmx.order.domain.model.RoutedHubOrderDraft(
                                new LegalEntityCode("LOC"),
                                new PortfolioNumber("PAR-EUR-001"),
                                RestTestInstitutionCode(),
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
        hub.assign(new TraderId("alice"), Instant.parse("2026-05-10T10:00:00Z"));
        hub.execute(
                new BigDecimal("3.55"),
                "BankCo International",
                RestTestInstitutionCode(),
                new DealingReference("DL-001"),
                new ContractNumber("CN-NEW"),
                new TraderId("alice"),
                Instant.parse("2026-05-10T11:00:00Z"));
        return hub;
    }

    private static String RestTestInstitutionCode() {
        return "BI-01";
    }

    private static MoneyMarketOrder subscribedAssignedExecuted() {
        MoneyMarketOrder order =
                MoneyMarketOrder.create(
                        new ExternalOrderReference("PM-PAYLOAD-" + Instant.now().toEpochMilli()),
                        new LegalEntityCode("LOC"),
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        new PortfolioNumber("PF-001"),
                        "EUR",
                        new BigDecimal("1000000.00"),
                        TODAY.plusDays(5),
                        new BigDecimal("3.25"),
                        Tenor._3M, null, null, "BNKCO", "BankCo",
                        TODAY);
        order.assign(new TraderId("alice"), Instant.parse("2026-05-10T10:00:00Z"));
        order.execute(
                new BigDecimal("3.55"),
                "BankCo International",
                "HSBC-01",
                new DealingReference("DL-001"),
                new ContractNumber("CN-NEW"),
                new TraderId("alice"),
                Instant.parse("2026-05-10T11:00:00Z"));
        order.markHandoffPending();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXECUTED);
        return order;
    }
}
