package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.ExecutionDetails;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.RoutingId;

import java.time.Instant;
import java.util.Objects;

/**
 * Leg-B inbound outcome decoded from the {@code mmx.routed-order-outcome.<org>} topic by the client
 * deployment. This is the authoritative lifecycle mirror for a remote routed pair: it carries only
 * the cross-boundary correlation key {@code (originatingLegalEntityCode, routingId)} plus the
 * terminal/non-terminal signal and its timestamp — no internal order UUID crosses the boundary.
 *
 * <p>Spec: {@code order-routing} — silence is never terminal. {@code ACCEPTED} is the non-terminal
 * accept signal; {@code EXECUTED} / {@code CANCELLED} / {@code REJECTED} are the terminal outcomes.
 * The {@code Executed} variant carries the hub's {@link ExecutionDetails} so the client can mirror
 * the hub-side execution (rate, institution, dealing reference, execution time) without re-deriving
 * it; the client generates its own deposit contract number locally for a subscription.
 */
public sealed interface RemoteOrderOutcome {

    LegalEntityCode originatingLegalEntityCode();

    RoutingId routingId();

    /** Non-terminal accept signal; closes {@code Received→Routed} on the client side. */
    record Accepted(LegalEntityCode originatingLegalEntityCode, RoutingId routingId, Instant acceptedAt)
            implements RemoteOrderOutcome {
        public Accepted {
            Objects.requireNonNull(originatingLegalEntityCode, "originatingLegalEntityCode");
            Objects.requireNonNull(routingId, "routingId");
            Objects.requireNonNull(acceptedAt, "acceptedAt");
        }
    }

    /** Terminal execution outcome; mirrors the hub-side execution onto the client side. */
    record Executed(
            LegalEntityCode originatingLegalEntityCode,
            RoutingId routingId,
            ExecutionDetails hubExecution,
            Instant executedAt)
            implements RemoteOrderOutcome {
        public Executed {
            Objects.requireNonNull(originatingLegalEntityCode, "originatingLegalEntityCode");
            Objects.requireNonNull(routingId, "routingId");
            Objects.requireNonNull(hubExecution, "hubExecution");
            Objects.requireNonNull(executedAt, "executedAt");
        }
    }

    /** Terminal cancel outcome. */
    record Cancelled(LegalEntityCode originatingLegalEntityCode, RoutingId routingId, Instant cancelledAt)
            implements RemoteOrderOutcome {
        public Cancelled {
            Objects.requireNonNull(originatingLegalEntityCode, "originatingLegalEntityCode");
            Objects.requireNonNull(routingId, "routingId");
            Objects.requireNonNull(cancelledAt, "cancelledAt");
        }
    }

    /** Terminal trader-reject outcome. */
    record Rejected(
            LegalEntityCode originatingLegalEntityCode,
            RoutingId routingId,
            String reason,
            Instant rejectedAt)
            implements RemoteOrderOutcome {
        public Rejected {
            Objects.requireNonNull(originatingLegalEntityCode, "originatingLegalEntityCode");
            Objects.requireNonNull(routingId, "routingId");
            Objects.requireNonNull(rejectedAt, "rejectedAt");
        }
    }
}
