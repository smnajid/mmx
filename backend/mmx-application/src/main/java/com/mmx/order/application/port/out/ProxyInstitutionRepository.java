package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ThinProxyInstitution;

import java.util.List;
import java.util.Optional;

public interface ProxyInstitutionRepository {

    List<ThinProxyInstitution> findAll();

    List<ThinProxyInstitution> findByClientLegalEntity(LegalEntityCode clientLegalEntityCode);

    Optional<ThinProxyInstitution> findByInstitutionCode(String institutionCode);

    int maxSuffixForAcronym(String acronymBase);

    ThinProxyInstitution save(ThinProxyInstitution proxy);
}
