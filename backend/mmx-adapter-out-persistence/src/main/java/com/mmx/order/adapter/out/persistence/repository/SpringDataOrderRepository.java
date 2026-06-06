package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, UUID> {

    Optional<OrderEntity> findByExternalOrderReference(String externalOrderReference);

    List<OrderEntity> findByStatusAndOrderType(String status, String orderType);

    Page<OrderEntity> findByStatusAndOrderTypeOrderByValueDateAsc(String status, String orderType, Pageable pageable);

    Page<OrderEntity> findByStatusAndOrderTypeAndValueDateBetweenOrderByValueDateAsc(
            String status,
            String orderType,
            LocalDate valueDateStartInclusive,
            LocalDate valueDateEndInclusive,
            Pageable pageable);

    List<OrderEntity> findByAssignedTraderIdAndStatus(String assignedTraderId, String status);

    Optional<OrderEntity> findByGeneratedContractNumberAndOrderTypeAndOrderOperationAndStatus(
            String generatedContractNumber,
            String orderType,
            String orderOperation,
            String status);
}
