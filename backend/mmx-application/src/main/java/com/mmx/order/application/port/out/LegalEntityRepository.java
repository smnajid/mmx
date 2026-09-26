package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.OrganisationCode;

import java.util.List;
import java.util.Optional;

public interface LegalEntityRepository {

    Optional<LegalEntity> findByCode(LegalEntityCode code);

    List<LegalEntity> findByOrganisationCode(OrganisationCode organisationCode);

    boolean belongsToOrganisation(LegalEntityCode code, OrganisationCode organisationCode);
}
