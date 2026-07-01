package com.mmx.order.adapter.out.persistence.mapper;

import com.mmx.order.adapter.out.persistence.entity.InstitutionEntity;
import com.mmx.order.domain.model.Institution;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InstitutionPersistenceMapper {

    public Institution toDomain(InstitutionEntity entity) {
        return new Institution(entity.getInstitutionCode(), entity.getDisplayName(), entity.isActive());
    }

    public InstitutionEntity toEntity(Institution institution, String legalEntityCode, Instant now) {
        InstitutionEntity entity = new InstitutionEntity();
        entity.setInstitutionCode(institution.getInstitutionCode());
        entity.setDisplayName(institution.getDisplayName());
        entity.setActive(institution.isActive());
        entity.setLegalEntityCode(legalEntityCode);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    public InstitutionEntity toEntity(Institution institution, Instant now) {
        return toEntity(institution, "LOC", now);
    }

    public void updateEntity(InstitutionEntity entity, Institution institution, Instant now) {
        entity.setDisplayName(institution.getDisplayName());
        entity.setActive(institution.isActive());
        entity.setUpdatedAt(now);
    }
}
