package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, UUID> {

    Optional<OrderEntity> findByExternalOrderReference(String externalOrderReference);

    List<OrderEntity> findByStatusAndOrderType(String status, String orderType);

    List<OrderEntity> findByAssignedTraderIdAndStatus(String assignedTraderId, String status);
}
