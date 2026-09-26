package com.mmx.order.adapter.out.messaging;

import com.mmx.order.adapter.out.messaging.entity.RoutingOutcomeOutboxEntity;
import com.mmx.order.adapter.out.messaging.repository.SpringDataRoutingOutcomeOutboxRepository;
import com.mmx.order.application.port.out.RoutingOutcomeOutbox;
import com.mmx.order.domain.model.MoneyMarketOrder;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA-backed {@link RoutingOutcomeOutbox}. Each {@code schedule*} call is {@code Propagation.MANDATORY}
 * — it commits in the caller's transaction, so the outbox row is exactly as durable as the hub-side
 * transition it mirrors.
 */
public class RoutingOutcomeOutboxAdapter implements RoutingOutcomeOutbox {

    private static final String PENDING = "PENDING";

    private final SpringDataRoutingOutcomeOutboxRepository repository;
    private final Clock clock;

    public RoutingOutcomeOutboxAdapter(SpringDataRoutingOutcomeOutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleAccepted(MoneyMarketOrder hubOrder, Instant acceptedAt) {
        save(hubOrder, "ACCEPTED", RoutingOutcomeV1PayloadMapper.accepted(hubOrder, acceptedAt));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleExecuted(MoneyMarketOrder hubOrder, Instant executedAt) {
        save(hubOrder, "EXECUTED", RoutingOutcomeV1PayloadMapper.executed(hubOrder, executedAt));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleCancelled(MoneyMarketOrder hubOrder, Instant cancelledAt) {
        save(hubOrder, "CANCELLED", RoutingOutcomeV1PayloadMapper.cancelled(hubOrder, cancelledAt));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleRejected(MoneyMarketOrder hubOrder, String reason, Instant rejectedAt) {
        save(hubOrder, "REJECTED", RoutingOutcomeV1PayloadMapper.rejected(hubOrder, reason, rejectedAt));
    }

    private void save(MoneyMarketOrder hubOrder, String outcomeType, String payload) {
        var entity = new RoutingOutcomeOutboxEntity(
                UUID.randomUUID(),
                hubOrder.getId(),
                hubOrder.getOriginatingLegalEntityCode() != null
                        ? hubOrder.getOriginatingLegalEntityCode().value()
                        : null,
                hubOrder.getRoutingId() != null ? hubOrder.getRoutingId().value() : null,
                outcomeType,
                payload,
                PENDING,
                clock.instant());
        repository.save(entity);
    }
}
