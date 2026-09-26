package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MmxUserId;
import com.mmx.order.domain.model.Role;

public interface ReScopeUseCase {

    ScopeContext reScope(MmxUserId userId, LegalEntityCode legalEntityCode, Role role);
}
