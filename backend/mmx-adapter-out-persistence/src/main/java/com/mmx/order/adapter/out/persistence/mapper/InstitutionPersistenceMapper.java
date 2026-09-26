package com.mmx.order.adapter.out.persistence.mapper;

import com.mmx.order.adapter.out.persistence.entity.InstitutionEntity;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InstitutionPersistenceMapper {

    public Institution toDomain(InstitutionEntity entity) {
        HubInstitutionLink hubLink =
                entity.getHubInstitutionCode() == null
                        ? null
                        : new HubInstitutionLink(
                                new LegalEntityCode(entity.getHubLegalEntityCode()), entity.getHubInstitutionCode());
        return new Institution(
                entity.getInstitutionCode(),
                entity.getDisplayName(),
                new LegalEntityCode(entity.getLegalEntityCode()),
                hubLink,
                CounterpartyAccounts.of(entity.getTermCounterpartyAccount(), entity.getOnCallCounterpartyAccount()),
                entity.isActive(),
                entity.getVersion());
    }

    public InstitutionEntity toEntity(Institution institution, String legalEntityCode, Instant now) {
        InstitutionEntity entity = new InstitutionEntity();
        entity.setInstitutionCode(institution.getInstitutionCode());
        entity.setLegalEntityCode(legalEntityCode);
        institution.getHubLink().ifPresent(link -> {
            entity.setHubLegalEntityCode(link.hubLegalEntityCode().value());
            entity.setHubInstitutionCode(link.hubInstitutionCode());
        });
        entity.setCreatedAt(now);
        updateEntity(entity, institution, now);
        return entity;
    }

    public void updateEntity(InstitutionEntity entity, Institution institution, Instant now) {
        entity.setDisplayName(institution.getDisplayName());
        entity.setActive(institution.isActive());
        entity.setTermCounterpartyAccount(institution.getCounterpartyAccounts().term().orElse(null));
        entity.setOnCallCounterpartyAccount(institution.getCounterpartyAccounts().onCall().orElse(null));
        entity.setVersion(institution.getVersion());
        entity.setUpdatedAt(now);
    }
}
