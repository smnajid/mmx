package com.mmx.order.adapter.out.messaging;

import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxEntity;
import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxRowStatus;
import com.mmx.order.adapter.out.messaging.repository.SpringDataBackOfficeOutboxRepository;
import com.mmx.order.application.port.out.ExecutionHandoffOutbox;
import com.mmx.order.application.port.out.ExecutionHandoffRoutingContext;
import com.mmx.order.domain.model.MoneyMarketOrder;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class ExecutionHandoffOutboxAdapter implements ExecutionHandoffOutbox {

    private final SpringDataBackOfficeOutboxRepository repository;
    private final OrderExecutedV1PayloadMapper payloadMapper;

    public ExecutionHandoffOutboxAdapter(
            SpringDataBackOfficeOutboxRepository repository,
            OrderExecutedV1PayloadMapper payloadMapper) {
        this.repository = repository;
        this.payloadMapper = payloadMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void schedule(MoneyMarketOrder executedOrder, ExecutionHandoffRoutingContext routingContext) {
        String payload = payloadMapper.toJsonPayload(executedOrder, routingContext);
        BackOfficeOutboxEntity row =
                new BackOfficeOutboxEntity(
                        UUID.randomUUID(),
                        executedOrder.getId(),
                        payload,
                        BackOfficeOutboxRowStatus.PENDING,
                        Instant.now());
        repository.save(row);
    }
}
