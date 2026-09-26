package com.mmx.order.adapter.out.messaging.repository;

import com.mmx.order.adapter.out.messaging.entity.RoutingOutcomeOutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataRoutingOutcomeOutboxRepository extends JpaRepository<RoutingOutcomeOutboxEntity, UUID> {

    List<RoutingOutcomeOutboxEntity> findByStatusOrderByCreatedAtAsc(String status);

    List<RoutingOutcomeOutboxEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
