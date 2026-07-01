package com.mmx.order.adapter.out.persistence.mapper;

import com.mmx.order.adapter.out.persistence.entity.InstitutionEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ThinProxyInstitution;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ProxyInstitutionPersistenceMapper {

    public ThinProxyInstitution toDomain(InstitutionEntity entity) {
        return new ThinProxyInstitution(
                entity.getInstitutionCode(),
                entity.getDisplayName(),
                new LegalEntityCode(entity.getHubLegalEntityCode()),
                entity.getHubInstitutionCode(),
                entity.isActive());
    }

    public InstitutionEntity toEntity(ThinProxyInstitution proxy, String clientLegalEntityCode, Instant now) {
        InstitutionEntity entity = new InstitutionEntity();
        entity.setInstitutionCode(proxy.getInstitutionCode());
        entity.setDisplayName(proxy.getDisplayName());
        entity.setActive(proxy.isActive());
        entity.setLegalEntityCode(clientLegalEntityCode);
        entity.setHubLegalEntityCode(proxy.getHubLegalEntityCode().value());
        entity.setHubInstitutionCode(proxy.getHubInstitutionCode());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    public void updateEntity(InstitutionEntity entity, ThinProxyInstitution proxy, Instant now) {
        entity.setDisplayName(proxy.getDisplayName());
        entity.setActive(proxy.isActive());
        entity.setUpdatedAt(now);
    }
}
