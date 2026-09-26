package com.mmx.order.adapter.out.messaging.repository;

import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxRowStatus;
import com.mmx.order.adapter.out.messaging.entity.OnCallRateHandoffOutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataOnCallRateHandoffOutboxRepository
        extends JpaRepository<OnCallRateHandoffOutboxEntity, UUID> {

    List<OnCallRateHandoffOutboxEntity> findByStatusOrderByCreatedAtAsc(
            BackOfficeOutboxRowStatus status, Pageable pageable);
}
