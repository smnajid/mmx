package com.mmx.order.adapter.out.persistence.mapper;

import com.mmx.order.adapter.out.persistence.entity.LegalEntityEntity;
import com.mmx.order.adapter.out.persistence.entity.OrganisationEntity;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.Organisation;
import com.mmx.order.domain.model.OrganisationCode;
import com.mmx.order.domain.model.TradingClientRole;
import com.mmx.order.domain.model.TradingHubRole;
import org.springframework.stereotype.Component;

@Component
public class TenancyPersistenceMapper {

    public Organisation toDomain(OrganisationEntity entity) {
        return new Organisation(new OrganisationCode(entity.getCode()));
    }

    public LegalEntity toDomain(LegalEntityEntity entity) {
        LegalEntityCode code = new LegalEntityCode(entity.getCode());
        OrganisationCode organisationCode = new OrganisationCode(entity.getOrganisationCode());
        if ("TRADING_HUB".equals(entity.getRole())) {
            return LegalEntity.tradingHub(code, organisationCode);
        }
        LegalEntityCode hubCode = new LegalEntityCode(entity.getConnectedHubCode());
        LegalEntity hub = LegalEntity.tradingHub(hubCode, organisationCode);
        return LegalEntity.tradingClient(code, organisationCode, hub);
    }

    public OrganisationEntity toEntity(Organisation organisation) {
        return new OrganisationEntity(organisation.getCode().value());
    }

    public LegalEntityEntity toEntity(LegalEntity legalEntity) {
        LegalEntityEntity entity = new LegalEntityEntity();
        entity.setCode(legalEntity.getCode().value());
        entity.setOrganisationCode(legalEntity.getOrganisationCode().value());
        if (legalEntity.isTradingHub()) {
            entity.setRole("TRADING_HUB");
            entity.setConnectedHubCode(null);
        } else {
            entity.setRole("TRADING_CLIENT");
            TradingClientRole clientRole = (TradingClientRole) legalEntity.getRole();
            entity.setConnectedHubCode(clientRole.connectedHubCode().value());
        }
        return entity;
    }
}
