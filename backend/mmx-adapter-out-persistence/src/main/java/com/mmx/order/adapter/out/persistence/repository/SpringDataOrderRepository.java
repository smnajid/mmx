package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    List<OrderEntity> findByPortfolioNumberAndOrderTypeAndOrderOperationAndStatusOrderByValueDateAsc(
            String portfolioNumber,
            String orderType,
            String orderOperation,
            String status);

    @Query(
            """
            SELECT DISTINCT o.sourceContractNumber
            FROM OrderEntity o
            WHERE o.sourceContractNumber IN :contractNumbers
              AND o.orderOperation = :orderOperation
              AND o.status <> :cancelledStatus
            """)
    Set<String> findRedeemedContractNumbers(
            @Param("contractNumbers") List<String> contractNumbers,
            @Param("orderOperation") String orderOperation,
            @Param("cancelledStatus") String cancelledStatus);
}
