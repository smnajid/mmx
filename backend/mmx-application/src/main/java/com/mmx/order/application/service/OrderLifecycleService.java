package com.mmx.order.application.service;

import com.mmx.order.application.command.CancelOrderCommand;
import com.mmx.order.application.command.RejectOrderCommand;
import com.mmx.order.application.port.in.CancelOrderUseCase;
import com.mmx.order.application.port.in.RejectOrderUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.RoutedPairLocalityResolver;
import com.mmx.order.application.port.out.RoutingOutcomeOutbox;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.model.HubLocality;
import com.mmx.order.domain.model.MoneyMarketOrder;

public final class OrderLifecycleService implements CancelOrderUseCase, RejectOrderUseCase {

    static final String EVENT_ORDER_CANCELLED = "ORDER_CANCELLED";
    static final String EVENT_ORDER_REJECTED = "ORDER_REJECTED";

    private final OrderRepository orderRepository;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final RoutedOrderOutcomePropagation routedOrderOutcomePropagation;
    private final RoutingOutcomeOutbox routingOutcomeOutbox;
    private final RoutedPairLocalityResolver routedPairLocalityResolver;

    public OrderLifecycleService(
            OrderRepository orderRepository,
            AuditLogger auditLogger,
            Clock clock,
            RoutedOrderOutcomePropagation routedOrderOutcomePropagation,
            RoutingOutcomeOutbox routingOutcomeOutbox,
            RoutedPairLocalityResolver routedPairLocalityResolver) {
        this.orderRepository = orderRepository;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.routedOrderOutcomePropagation = routedOrderOutcomePropagation;
        this.routingOutcomeOutbox = routingOutcomeOutbox;
        this.routedPairLocalityResolver = routedPairLocalityResolver;
    }

    @Override
    public MoneyMarketOrder cancel(CancelOrderCommand command) {
        MoneyMarketOrder order =
                orderRepository
                        .findById(command.orderId())
                        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        var now = clock.now();
        order.cancel(now);
        MoneyMarketOrder saved = orderRepository.save(order);
        if (saved.isHubSideRoutedLink()) {
            if (isRemotePair(saved)) {
                // Cross-deployment pair: no in-process client-side order — mirror the outcome via
                // the leg-B outbox in this transaction (silence is never terminal).
                routingOutcomeOutbox.scheduleCancelled(saved, now);
            } else {
                routedOrderOutcomePropagation.propagateCancel(saved, now);
            }
        }
        auditLogger.log(saved.getId(), EVENT_ORDER_CANCELLED, command.traderId().value(), now);
        return saved;
    }

    @Override
    public MoneyMarketOrder reject(RejectOrderCommand command) {
        validateReject(command);
        MoneyMarketOrder order =
                orderRepository
                        .findById(command.orderId())
                        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        var now = clock.now();
        order.reject(command.traderId(), command.reason(), now);
        MoneyMarketOrder saved = orderRepository.save(order);
        if (saved.isHubSideRoutedLink()) {
            if (isRemotePair(saved)) {
                routingOutcomeOutbox.scheduleRejected(saved, saved.getRejectionReason(), now);
            } else {
                routedOrderOutcomePropagation.propagateReject(saved, saved.getRejectionReason(), now);
            }
        }
        auditLogger.log(saved.getId(), EVENT_ORDER_REJECTED, command.traderId().value(), now);
        return saved;
    }

    private static void validateReject(RejectOrderCommand command) {
        if (command.reason() == null || command.reason().isBlank()) {
            throw new InvalidOrderException("reason is required");
        }
    }

    private boolean isRemotePair(MoneyMarketOrder hubOrder) {
        return routedPairLocalityResolver.resolve(hubOrder.getOriginatingLegalEntityCode())
                == HubLocality.REMOTE;
    }
}
