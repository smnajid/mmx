package com.mmx.order.adapter.in.rest;

import com.mmx.order.application.port.in.ResolveUserScopeUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MmxUserId;
import com.mmx.order.domain.model.Role;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

final class OrderControllerTestSupport {

    static final ScopeContext DEFAULT_SCOPE =
            new ScopeContext(new LegalEntityCode("LOC"), Role.TRADER);

    private OrderControllerTestSupport() {}

    static void stubDefaultScope(ResolveUserScopeUseCase resolveUserScopeUseCase) {
        lenient().when(resolveUserScopeUseCase.resolve(any(MmxUserId.class))).thenReturn(DEFAULT_SCOPE);
    }
}
