package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.Institution;

import java.util.List;
import java.util.Optional;

/**
 * Read-only view of the connected TradingHub's native institutions, as seen by a TradingClient: in-process
 * for a same-Organisation client, a live read of the hub deployment for a remote client.
 */
public interface HubInstitutionCatalog {

    List<Institution> findAll();

    Optional<Institution> findByInstitutionCode(String hubInstitutionCode);
}
