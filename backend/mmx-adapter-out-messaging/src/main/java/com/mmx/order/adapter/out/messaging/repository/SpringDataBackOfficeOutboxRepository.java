package com.mmx.order.adapter.out.messaging.repository;

import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxEntity;
import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxRowStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;

public interface SpringDataBackOfficeOutboxRepository extends JpaRepository<BackOfficeOutboxEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BackOfficeOutboxEntity> findByStatusOrderByCreatedAtAsc(
            BackOfficeOutboxRowStatus status, Pageable pageable);
}
