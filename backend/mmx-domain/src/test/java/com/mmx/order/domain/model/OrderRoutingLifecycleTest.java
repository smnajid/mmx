package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@Tag("fast")

@DisplayName("Order routing lifecycle")
class OrderRoutingLifecycleTest {

    @Nested
    @DisplayName("client-side routed order transitions")
    class ClientRouted {

        @Test
        void received_to_routed_to_executed() {
            OrderStatus routed =
                    OrderStatus.RECEIVED.transitionTo(OrderStatus.ROUTED, OrderLifecycleKind.ROUTED_CLIENT);
            assertThat(routed).isEqualTo(OrderStatus.ROUTED);

            OrderStatus executed =
                    OrderStatus.ROUTED.transitionTo(OrderStatus.EXECUTED, OrderLifecycleKind.ROUTED_CLIENT);
            assertThat(executed).isEqualTo(OrderStatus.EXECUTED);
        }

        @Test
        void routed_cannot_transition_to_assigned() {
            assertThatThrownBy(
                            () ->
                                    OrderStatus.ROUTED.transitionTo(
                                            OrderStatus.ASSIGNED, OrderLifecycleKind.ROUTED_CLIENT))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void received_cannot_transition_to_assigned_for_routed_client() {
            assertThatThrownBy(
                            () ->
                                    OrderStatus.RECEIVED.transitionTo(
                                            OrderStatus.ASSIGNED, OrderLifecycleKind.ROUTED_CLIENT))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void assigned_cannot_transition_to_routed() {
            assertThatThrownBy(
                            () ->
                                    OrderStatus.ASSIGNED.transitionTo(
                                            OrderStatus.ROUTED, OrderLifecycleKind.ROUTED_CLIENT))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    @Nested
    @DisplayName("hub-side desk order never uses ROUTED")
    class HubDesk {

        @Test
        void received_to_assigned_not_routed() {
            OrderStatus assigned =
                    OrderStatus.RECEIVED.transitionTo(OrderStatus.ASSIGNED, OrderLifecycleKind.DESK);
            assertThat(assigned).isEqualTo(OrderStatus.ASSIGNED);
        }

        @Test
        void received_cannot_transition_to_routed_on_desk() {
            assertThatThrownBy(
                            () ->
                                    OrderStatus.RECEIVED.transitionTo(
                                            OrderStatus.ROUTED, OrderLifecycleKind.DESK))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void assigned_cannot_transition_to_routed_on_desk() {
            assertThatThrownBy(
                            () ->
                                    OrderStatus.ASSIGNED.transitionTo(
                                            OrderStatus.ROUTED, OrderLifecycleKind.DESK))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }
}
