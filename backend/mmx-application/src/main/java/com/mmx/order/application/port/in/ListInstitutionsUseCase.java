package com.mmx.order.application.port.in;

import com.mmx.order.application.port.in.ScopeContext;

public interface ListInstitutionsUseCase {

    InstitutionListView list(ScopeContext scope);
}
