package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ApplyRemoteOrderOutcomeUseCase;
import com.mmx.order.application.port.in.RemoteOrderOutcome;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.domain.exception.RoutedOrderPairIntegrityException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;

import java.util.Objects;

/**
 * Client-deployment (e.g. CGED) leg-B inbound: applies a {@link RemoteOrderOutcome} to the linked
 * client-side order of a remote routed pair. Spec: {@code order-routing} — silence is never
 * terminal; leg B is the authoritative lifecycle mirror.
 *
 * <p>Idempotent under at-least-once Kafka: a re-delivered outcome the order already reflects is a
 * no-op ack (the offset advances). {@code ACCEPTED} is a no-op once the order has left
 * {@code RECEIVED}; a terminal outcome is a no-op only when the order holds the <em>matching</em>
 * terminal — a mismatched terminal (or a terminal arriving before {@code ROUTED}) surfaces the
 * underlying {@code InvalidStatusTransitionException} as a poison-message guard, never a silent
 * overwrite.
 */
public final class ApplyRemoteOrderOutcomeService implements ApplyRemoteOrderOutcomeUseCase {

    private final OrderRepository orderRepository;
    private final ReferenceGenerator referenceGenerator;

    public ApplyRemoteOrderOutcomeService(
            OrderRepository orderRepository, ReferenceGenerator referenceGenerator) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.referenceGenerator = Objects.requireNonNull(referenceGenerator);
    }

    @Override
    public void apply(RemoteOrderOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        MoneyMarketOrder client = loadClient(outcome);

        switch (outcome) {
            case RemoteOrderOutcome.Accepted a -> applyAccepted(client, a);
            case RemoteOrderOutcome.Executed e -> applyExecuted(client, e);
            case RemoteOrderOutcome.Cancelled c -> applyCancelled(client, c);
            case RemoteOrderOutcome.Rejected r -> applyRejected(client, r);
        }
    }

    private void applyAccepted(MoneyMarketOrder client, RemoteOrderOutcome.Accepted a) {
        if (client.getStatus() != OrderStatus.RECEIVED) {
            return;
        }
        client.applyAcceptedFromLegB(a.routingId(), a.acceptedAt());
        orderRepository.save(client);
    }

    private void applyExecuted(MoneyMarketOrder client, RemoteOrderOutcome.Executed e) {
        if (client.getStatus() == OrderStatus.EXECUTED) {
            return;
        }
        ContractNumber clientContract =
                client.getOrderOperation() == OrderOperation.SUBSCRIPTION
                        ? referenceGenerator.generateContractNumber()
                        : client.getSourceContractNumber();
        client.propagateExecutionFromHub(
                e.hubExecution(), client.getCounterparty(), clientContract, e.executedAt());
        orderRepository.save(client);
    }

    private void applyCancelled(MoneyMarketOrder client, RemoteOrderOutcome.Cancelled c) {
        if (client.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        client.propagateCancelFromHub(c.cancelledAt());
        orderRepository.save(client);
    }

    private void applyRejected(MoneyMarketOrder client, RemoteOrderOutcome.Rejected r) {
        if (client.getStatus() == OrderStatus.REJECTED) {
            return;
        }
        String reason = r.reason() != null && !r.reason().isBlank() ? r.reason() : "Rejected at hub";
        client.propagateRejectFromHub(reason, r.rejectedAt());
        orderRepository.save(client);
    }

    private MoneyMarketOrder loadClient(RemoteOrderOutcome outcome) {
        MoneyMarketOrder client =
                orderRepository
                        .findRoutedClientOrderByRoutingId(outcome.routingId())
                        .orElseThrow(
                                () ->
                                        new RoutedOrderPairIntegrityException(
                                                "No client-side order for routing id " + outcome.routingId()));
        if (!client.getLegalEntityCode().equals(outcome.originatingLegalEntityCode())) {
            throw new RoutedOrderPairIntegrityException(
                    "leg-B outcome originatingLegalEntityCode " + outcome.originatingLegalEntityCode()
                            + " does not match client-side order legal entity " + client.getLegalEntityCode()
                            + " for routing id " + outcome.routingId());
        }
        return client;
    }
}
