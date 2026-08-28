package com.mmx.order.adapter.out.persistence;

import com.mmx.order.application.port.out.CrossOrgMembershipPort;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.TradingClientRole;

import java.util.Optional;

/**
 * JPA-backed {@link CrossOrgMembershipPort}. Checks whether a given {@link LegalEntityCode} is a
 * TradingClient connected to this hub by looking up the legal entity and verifying its
 * {@code connectedHubCode} matches this hub's code.
 */
public class JpaCrossOrgMembershipAdapter implements CrossOrgMembershipPort {

    private final LegalEntityRepository legalEntityRepository;
    private final LegalEntityCode hubLegalEntityCode;

    public JpaCrossOrgMembershipAdapter(
            LegalEntityRepository legalEntityRepository, LegalEntityCode hubLegalEntityCode) {
        this.legalEntityRepository = legalEntityRepository;
        this.hubLegalEntityCode = hubLegalEntityCode;
    }

    @Override
    public boolean isRemoteTradingClientOfThisHub(LegalEntityCode code) {
        Optional<LegalEntity> entity = legalEntityRepository.findByCode(code);
        if (entity.isEmpty()) {
            return false;
        }
        var role = entity.get().getRole();
        if (!(role instanceof TradingClientRole clientRole)) {
            return false;
        }
        return clientRole.connectedHubCode().equals(hubLegalEntityCode);
    }
}
