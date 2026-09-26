package com.mmx.order.adapter.out.messaging.repository;

import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxRowStatus;
import com.mmx.order.adapter.out.messaging.entity.InstitutionExportOutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataInstitutionExportOutboxRepository
        extends JpaRepository<InstitutionExportOutboxEntity, UUID> {

    List<InstitutionExportOutboxEntity> findByStatusOrderByCreatedAtAsc(
            BackOfficeOutboxRowStatus status, Pageable pageable);
}
