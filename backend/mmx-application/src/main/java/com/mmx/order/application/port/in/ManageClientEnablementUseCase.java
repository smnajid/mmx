package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;

import java.util.List;
import java.util.Set;

/**
 * ClientRepresentative control of the client enablement of an onboarded institution, per currency: a full
 * replacement bounded by the current grant and gated by the OrderType's counterparty account. Stored in the
 * client's own deployment only; records no institution export.
 */
public interface ManageClientEnablementUseCase {

    Institution update(UpdateCommand command);

    /**
     * One entry per currency that is granted (active grant) or client-enabled, sorted by currency; empty for a
     * native institution. A value enabled but not granted is capped out of the effective enablement.
     */
    List<CurrencyEnablement> enablementsOf(String institutionCode);

    record UpdateCommand(
            ScopeContext scope,
            String institutionCode,
            String currency,
            Set<Tenor> enabledTenors,
            Set<NoticePeriod> enabledNoticePeriods) {}

    record CurrencyEnablement(
            String currency,
            Set<Tenor> grantedTenors,
            Set<NoticePeriod> grantedNoticePeriods,
            Set<Tenor> enabledTenors,
            Set<NoticePeriod> enabledNoticePeriods) {}
}
