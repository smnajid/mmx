package com.mmx.order.domain.policy;

import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderRoutingFieldMappingPolicy")
class OrderRoutingFieldMappingPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);

    @Test
    void maps_client_order_to_hub_side_fields_and_trace() {
        MoneyMarketOrder clientOrder =
                MoneyMarketOrder.create(
                        new ExternalOrderReference("PM-ROUTE-001"),
                        new LegalEntityCode("PAR"),
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        new PortfolioNumber("PAR-PM-77"),
                        "EUR",
                        new BigDecimal("1000000.00"),
                        TODAY.plusDays(2),
                        new BigDecimal("2.50000000"),
                        Tenor._3M,
                        null,
                        null,
                        "BNP-PAR",
                        "BNP via LOC",
                        TODAY);

        GlobalAccount globalAccount =
                new GlobalAccount(
                        new LegalEntityCode("PAR"),
                        new LegalEntityCode("LOC"),
                        "EUR",
                        "PAR-EUR-001");

        RoutingId routingId = RoutingId.fromClientOrderId(clientOrder.getId());

        OrderRoutingFieldMappingPolicy.RoutedHubOrderDraft draft =
                OrderRoutingFieldMappingPolicy.mapToHubSide(
                        clientOrder,
                        globalAccount,
                        routingId,
                        "BNP",
                        "BNP");

        assertThat(draft.portfolioNumber()).isEqualTo(new PortfolioNumber("PAR-EUR-001"));
        assertThat(draft.institutionCode()).isEqualTo("BNP");
        assertThat(draft.counterparty()).isEqualTo("BNP");
        assertThat(draft.currency()).isEqualTo("EUR");
        assertThat(draft.amount()).isEqualByComparingTo(new BigDecimal("1000000.00"));
        assertThat(draft.valueDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(draft.orderType()).isEqualTo(OrderType.TERM);
        assertThat(draft.orderOperation()).isEqualTo(OrderOperation.SUBSCRIPTION);
        assertThat(draft.tenor()).isEqualTo(Tenor._3M);
        assertThat(draft.minimumRate()).isEqualByComparingTo(new BigDecimal("2.50000000"));
        assertThat(draft.routingId()).isEqualTo(routingId);
        assertThat(draft.originatingLegalEntityCode()).isEqualTo(new LegalEntityCode("PAR"));
        assertThat(draft.originatingExternalOrderReference())
                .isEqualTo(new ExternalOrderReference("PM-ROUTE-001"));
        assertThat(draft.hubLegalEntityCode()).isEqualTo(new LegalEntityCode("LOC"));
    }
}
