package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.List;

public interface ManageGlobalAccountsUseCase {

    List<GlobalAccount> listForHub(ScopeContext scope);

    GlobalAccount upsert(UpsertCommand command);

    record UpsertCommand(
            ScopeContext scope,
            LegalEntityCode clientLegalEntityCode,
            String currency,
            String accountRef) {}
}
