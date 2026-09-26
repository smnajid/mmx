package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.ClientEnablement;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.List;

/** Client-owned enablement per (onboarded institution, currency), stored in the client's own deployment. */
public interface ClientEnablementRepository {

    /** The stored enablement, or an empty one when nothing was ever enabled. */
    ClientEnablement find(String institutionCode, String currency);

    List<ClientEnablement> findByInstitutionCode(String institutionCode);

    /** Replaces the currency's tenor and notice-period sets. */
    void save(LegalEntityCode owningLegalEntityCode, ClientEnablement enablement);
}
