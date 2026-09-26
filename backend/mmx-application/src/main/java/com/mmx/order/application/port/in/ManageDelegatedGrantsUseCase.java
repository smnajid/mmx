package com.mmx.order.application.port.in;

import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;

import java.util.List;
import java.util.Set;

/** Grant CRUD restricted to a Trader on the TradingHub. The hub LegalEntity is the caller's scope. */
public interface ManageDelegatedGrantsUseCase {

    List<DelegatedInstitutionGrant> listGrants(ScopeContext scope);

    List<DelegatedInstitutionGrant> listClientGrants(ScopeContext scope);

    DelegatedInstitutionGrant getByKey(DelegatedGrantKey key, ScopeContext scope);

    DelegatedInstitutionGrant createGrant(CreateGrantCommand command);

    DelegatedInstitutionGrant updateGrant(UpdateGrantCommand command);

    DelegatedInstitutionGrant deactivateGrant(DelegatedGrantKey key, ScopeContext scope);

    DelegatedInstitutionGrant reactivateGrant(DelegatedGrantKey key, ScopeContext scope);

    record CreateGrantCommand(
            ScopeContext scope,
            String hubInstitutionCode,
            LegalEntityCode clientLegalEntityCode,
            String currency,
            Set<Tenor> enabledTenors,
            Set<NoticePeriod> enabledNoticePeriods) {}

    record UpdateGrantCommand(
            ScopeContext scope,
            String hubInstitutionCode,
            LegalEntityCode clientLegalEntityCode,
            String currency,
            Set<Tenor> enabledTenors,
            Set<NoticePeriod> enabledNoticePeriods) {}
}
