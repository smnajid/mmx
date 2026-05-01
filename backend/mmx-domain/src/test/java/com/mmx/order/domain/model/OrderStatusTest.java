package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderStatus transitions")
class OrderStatusTest {

    // ── Valid transitions ────────────────────────────────────────────────────

    @Nested
    @DisplayName("RECEIVED can transition to")
    class FromReceived {

        @Test
        void assigned() {
            OrderStatus result = OrderStatus.RECEIVED.transitionTo(OrderStatus.ASSIGNED);
            assertThat(result).isEqualTo(OrderStatus.ASSIGNED);
        }

        @Test
        void cancelled() {
            OrderStatus result = OrderStatus.RECEIVED.transitionTo(OrderStatus.CANCELLED);
            assertThat(result).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        void rejected() {
            OrderStatus result = OrderStatus.RECEIVED.transitionTo(OrderStatus.REJECTED);
            assertThat(result).isEqualTo(OrderStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("ASSIGNED can transition to")
    class FromAssigned {

        @Test
        void received_on_unassign() {
            OrderStatus result = OrderStatus.ASSIGNED.transitionTo(OrderStatus.RECEIVED);
            assertThat(result).isEqualTo(OrderStatus.RECEIVED);
        }

        @Test
        void executed() {
            OrderStatus result = OrderStatus.ASSIGNED.transitionTo(OrderStatus.EXECUTED);
            assertThat(result).isEqualTo(OrderStatus.EXECUTED);
        }
    }

    // ── Invalid transitions ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Terminal statuses cannot transition")
    class TerminalStatuses {

        @Test
        void executed_is_terminal() {
            assertThatThrownBy(() -> OrderStatus.EXECUTED.transitionTo(OrderStatus.RECEIVED))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("EXECUTED");
        }

        @Test
        void cancelled_is_terminal() {
            assertThatThrownBy(() -> OrderStatus.CANCELLED.transitionTo(OrderStatus.RECEIVED))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("CANCELLED");
        }

        @Test
        void rejected_is_terminal() {
            assertThatThrownBy(() -> OrderStatus.REJECTED.transitionTo(OrderStatus.RECEIVED))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("REJECTED");
        }
    }

    @Nested
    @DisplayName("Invalid transitions throw InvalidStatusTransitionException")
    class InvalidTransitions {

        @Test
        void received_cannot_go_to_executed() {
            assertThatThrownBy(() -> OrderStatus.RECEIVED.transitionTo(OrderStatus.EXECUTED))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void assigned_cannot_go_to_cancelled() {
            assertThatThrownBy(() -> OrderStatus.ASSIGNED.transitionTo(OrderStatus.CANCELLED))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void assigned_cannot_go_to_rejected() {
            assertThatThrownBy(() -> OrderStatus.ASSIGNED.transitionTo(OrderStatus.REJECTED))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }
}
