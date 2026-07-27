package com.mmx.order.application.port.out;

import java.time.Instant;
import java.util.Objects;

/**
 * Leg-A response for a remote routed order — accept or reject, returned synchronously by the hub
 * deployment to the client deployment. No internal order UUID crosses the boundary.
 *
 * <p>Spec: {@code order-routing} — silence is never terminal; routing-failure reject is HTTP-only.
 * A {@link Accept} closes {@code Received→Routed} on the client-side order; a {@link Reject} closes
 * {@code Received→Rejected}. A leg-A transient failure (timeout, 5xx) is owned by the gateway's
 * retry/circuit-breaker and is NOT surfaced through this type — the use case sees only accept,
 * reject, or an exception from the gateway.
 */
public sealed interface RemoteRoutingResponse permits RemoteRoutingResponse.Accept, RemoteRoutingResponse.Reject {

    boolean isAccepted();

    boolean isRejected();

    default Accept asAccept() {
        if (!(this instanceof Accept accept)) {
            throw new IllegalStateException("Response is not an Accept: " + this);
        }
        return accept;
    }

    default Reject asReject() {
        if (!(this instanceof Reject reject)) {
            throw new IllegalStateException("Response is not a Reject: " + this);
        }
        return reject;
    }

    /**
     * Hub-side accept signal: the hub-side order was created in {@code RECEIVED}, a leg-B
     * {@code ACCEPTED} outbox row was committed same-tx, and the hub is now authoritative for the
     * lifecycle mirror. Closes the client-side order {@code Received→Routed}.
     */
    record Accept(Instant acceptedAt) implements RemoteRoutingResponse {
        public Accept {
            Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
        }

        @Override
        public boolean isAccepted() {
            return true;
        }

        @Override
        public boolean isRejected() {
            return false;
        }
    }

    /**
     * Routing-failure reject: grant/currency/tenor invalid at the hub's
     * {@code AcceptRoutedHubOrderUseCase}. No hub-side order is created, no leg-B event is emitted;
     * the HTTP reject response closes {@code Received→Rejected} on the client-side order directly.
     */
    record Reject(String reason) implements RemoteRoutingResponse {
        public Reject {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }

        @Override
        public boolean isAccepted() {
            return false;
        }

        @Override
        public boolean isRejected() {
            return true;
        }
    }
}
