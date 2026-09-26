package com.mmx.order.application.port.in;

/**
 * Client-deployment (e.g. CGED) leg-B inbound use case: apply a {@link RemoteOrderOutcome} to the
 * linked client-side order of a remote routed pair.
 *
 * <p>Spec: {@code order-routing} — silence is never terminal; leg B is the authoritative lifecycle
 * mirror. The use case loads the client-side order by {@code routingId} and applies the leg-B
 * outcome through the same domain transitions as the local synchronous path. It is idempotent under
 * at-least-once Kafka delivery: a re-delivered outcome that the order already reflects is a no-op
 * ack (the offset advances); a <em>mismatched</em> terminal (the order already holds a different
 * terminal) surfaces as an error (poison-message guard), never a silent overwrite.
 *
 * <p>If no client-side order exists for the {@code routingId}, the routed-pair invariant is broken
 * and a {@code RoutedOrderPairIntegrityException} is thrown.
 */
public interface ApplyRemoteOrderOutcomeUseCase {

    /**
     * @param outcome the leg-B outcome (decoded from the topic by the consumer adapter); carries the
     *     cross-boundary correlation key and the terminal/non-terminal signal
     */
    void apply(RemoteOrderOutcome outcome);
}
