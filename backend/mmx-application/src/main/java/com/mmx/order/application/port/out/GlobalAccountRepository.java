package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.List;
import java.util.Optional;

/** Persistence port for hub-managed global-account reference data. */
public interface GlobalAccountRepository {

    List<GlobalAccount> findAllByHub(LegalEntityCode hubLegalEntityCode);

    Optional<GlobalAccount> findByKey(
            LegalEntityCode clientLegalEntityCode, LegalEntityCode hubLegalEntityCode, String currency);

    GlobalAccount save(GlobalAccount account);
}
