package com.mmx.order.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.Tenor;
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

    private static MoneyMarketOrder subscribedAssignedExecuted() {
        MoneyMarketOrder order =
                MoneyMarketOrder.create(
                        new ExternalOrderReference("PM-PAYLOAD-" + Instant.now().toEpochMilli()),
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
