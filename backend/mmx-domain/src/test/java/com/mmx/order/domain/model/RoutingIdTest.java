package com.mmx.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
@Tag("fast")

@DisplayName("RoutingId")
class RoutingIdTest {

    @Test
    void same_client_order_id_produces_same_routing_id() {
        UUID clientOrderId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        RoutingId first = RoutingId.fromClientOrderId(clientOrderId);
        RoutingId second = RoutingId.fromClientOrderId(clientOrderId);

        assertThat(first).isEqualTo(second);
        assertThat(first.value()).isEqualTo(second.value());
    }

    @Test
    void distinct_client_orders_produce_distinct_routing_ids() {
        UUID firstClient = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID secondClient = UUID.fromString("11111111-2222-3333-4444-555555555555");

        RoutingId first = RoutingId.fromClientOrderId(firstClient);
        RoutingId second = RoutingId.fromClientOrderId(secondClient);

        assertThat(first).isNotEqualTo(second);
    }
}
