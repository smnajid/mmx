package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.Organisation;
import com.mmx.order.domain.model.OrganisationCode;

import java.util.Optional;

public interface OrganisationRepository {

    Optional<Organisation> findByCode(OrganisationCode code);
}
