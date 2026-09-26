package com.mmx.order.adapter.out.messaging;

import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxRowStatus;
import com.mmx.order.adapter.out.messaging.entity.OnCallRateHandoffOutboxEntity;
import com.mmx.order.adapter.out.messaging.repository.SpringDataOnCallRateHandoffOutboxRepository;
import com.mmx.order.application.port.out.OnCallRateHandoffOutbox;
import com.mmx.order.domain.model.OnCallRateSegment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class OnCallRateHandoffOutboxAdapter implements OnCallRateHandoffOutbox {

    private final SpringDataOnCallRateHandoffOutboxRepository repository;
    private final OnCallRateUpdatedV1PayloadMapper updatedMapper;
    private final OnCallRateCanceledV1PayloadMapper canceledMapper;

    public OnCallRateHandoffOutboxAdapter(
            SpringDataOnCallRateHandoffOutboxRepository repository,
            OnCallRateUpdatedV1PayloadMapper updatedMapper,
            OnCallRateCanceledV1PayloadMapper canceledMapper) {
        this.repository = repository;
        this.updatedMapper = updatedMapper;
        this.canceledMapper = canceledMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleUpdated(OnCallRateSegment segment) {
        String payload = updatedMapper.toJsonPayload(segment);
        repository.save(
                new OnCallRateHandoffOutboxEntity(
                        UUID.randomUUID(),
                        segment.getSegmentId(),
                        payload,
                        BackOfficeOutboxRowStatus.PENDING,
                        Instant.now()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleCanceled(UUID segmentId) {
        String payload = canceledMapper.toJsonPayload(segmentId);
        repository.save(
                new OnCallRateHandoffOutboxEntity(
                        UUID.randomUUID(),
                        segmentId,
                        payload,
                        BackOfficeOutboxRowStatus.PENDING,
                        Instant.now()));
    }
}
