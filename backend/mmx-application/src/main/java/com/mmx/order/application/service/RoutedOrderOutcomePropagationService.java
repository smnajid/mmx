package com.mmx.order.application.service;

import com.mmx.order.application.port.out.ExecutionHandoffRoutingContext;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.domain.exception.RoutedOrderPairIntegrityException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.RoutingId;

import java.time.Instant;

public final class RoutedOrderOutcomePropagationService implements RoutedOrderOutcomePropagation {

    private final OrderRepository orderRepository;
    private final ReferenceGenerator referenceGenerator;

    public RoutedOrderOutcomePropagationService(
            OrderRepository orderRepository, ReferenceGenerator referenceGenerator) {
        this.orderRepository = orderRepository;
        this.referenceGenerator = referenceGenerator;
    }

    @Override
    public ExecutionPropagationResult propagateExecution(MoneyMarketOrder hubOrder) {
        MoneyMarketOrder clientOrder = requireClientOrder(hubOrder);
        ContractNumber clientContract =
                hubOrder.getOrderOperation() == OrderOperation.SUBSCRIPTION
                        ? referenceGenerator.generateContractNumber()
                        : clientOrder.getSourceContractNumber();
        clientOrder.propagateExecutionFromHub(
                hubOrder.getExecutionDetails(),
                clientOrder.getCounterparty(),
                clientContract,
                hubOrder.getExecutionDetails().executionTime());
        MoneyMarketOrder savedClient = orderRepository.save(clientOrder);
        ExecutionHandoffRoutingContext handoffContext =
                new ExecutionHandoffRoutingContext(
                        hubOrder.getRoutingId(),
                        savedClient.getLegalEntityCode(),
                        savedClient.getId(),
                        savedClient.getPortfolioNumber().value(),
                        savedClient.getCounterparty());
        return new ExecutionPropagationResult(savedClient, handoffContext);
    }

    @Override
    public void propagateCancel(MoneyMarketOrder hubOrder, Instant now) {
        if (!isTerminalHubRoutedLink(hubOrder) || hubOrder.getStatus() != OrderStatus.CANCELLED) {
            return;
        }
        MoneyMarketOrder clientOrder = requireClientOrder(hubOrder);
        clientOrder.propagateCancelFromHub(now);
        orderRepository.save(clientOrder);
    }

    @Override
    public void propagateReject(MoneyMarketOrder hubOrder, String reason, Instant now) {
        if (!isTerminalHubRoutedLink(hubOrder) || hubOrder.getStatus() != OrderStatus.REJECTED) {
            return;
        }
        MoneyMarketOrder clientOrder = requireClientOrder(hubOrder);
        clientOrder.propagateRejectFromHub(reason != null ? reason : "Rejected at hub", now);
        orderRepository.save(clientOrder);
    }

    private MoneyMarketOrder requireClientOrder(MoneyMarketOrder hubOrder) {
        RoutingId routingId = hubOrder.getRoutingId();
        if (routingId == null) {
            throw new RoutedOrderPairIntegrityException("routingId is required for hub-side routed link");
        }
        return orderRepository
                .findRoutedClientOrderByRoutingId(routingId)
                .orElseThrow(
                        () ->
                                new RoutedOrderPairIntegrityException(
                                        "No client-side order for routing id " + routingId));
    }

    private static boolean isTerminalHubRoutedLink(MoneyMarketOrder hubOrder) {
        return hubOrder.isHubSideRoutedLink() && hubOrder.getRoutingId() != null;
    }
}
