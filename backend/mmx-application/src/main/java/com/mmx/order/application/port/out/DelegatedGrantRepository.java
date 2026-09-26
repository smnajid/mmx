package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.List;
import java.util.Optional;

public interface DelegatedGrantRepository {

    List<DelegatedInstitutionGrant> findAll();

    List<DelegatedInstitutionGrant> findByClientLegalEntityCode(LegalEntityCode clientLegalEntityCode);

    Optional<DelegatedInstitutionGrant> findByKey(DelegatedGrantKey key);

    boolean existsByKey(DelegatedGrantKey key);

    boolean existsActiveGrantForHubInstitutionAndClient(
            String hubInstitutionCode, LegalEntityCode clientLegalEntityCode);

    DelegatedInstitutionGrant save(DelegatedInstitutionGrant grant);
}
