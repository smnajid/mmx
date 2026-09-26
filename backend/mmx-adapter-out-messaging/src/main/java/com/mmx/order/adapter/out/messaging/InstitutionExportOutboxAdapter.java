package com.mmx.order.adapter.out.messaging;

import com.mmx.order.adapter.out.messaging.entity.BackOfficeOutboxRowStatus;
import com.mmx.order.adapter.out.messaging.entity.InstitutionExportOutboxEntity;
import com.mmx.order.adapter.out.messaging.repository.SpringDataInstitutionExportOutboxRepository;
import com.mmx.order.application.port.out.InstitutionExportOutbox;
import com.mmx.order.domain.model.Institution;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Records one {@code InstitutionUpdatedV1} row per exported-state change, in the caller's transaction, with
 * the payload frozen at record time. The relay publishes it to the owning LegalEntity's topic.
 */
public class InstitutionExportOutboxAdapter implements InstitutionExportOutbox {

    private final SpringDataInstitutionExportOutboxRepository repository;
    private final InstitutionUpdatedV1PayloadMapper mapper;

    public InstitutionExportOutboxAdapter(
            SpringDataInstitutionExportOutboxRepository repository, InstitutionUpdatedV1PayloadMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void schedule(Institution institution, ChangeReason reason) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        repository.save(
                new InstitutionExportOutboxEntity(
                        eventId,
                        institution.getOwningLegalEntityCode().value(),
                        institution.getInstitutionCode(),
                        institution.getVersion(),
                        mapper.toJsonPayload(institution, reason, eventId, now),
                        BackOfficeOutboxRowStatus.PENDING,
                        now));
    }
}
