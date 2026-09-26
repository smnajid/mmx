package com.mmx.order.adapter.in.rest.scope;

import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.Role;
import org.springframework.stereotype.Component;

@Component
public class RequestScopeContextProvider implements ScopeContextProvider {

    private static final ScopeContext DEFAULT_HUB_SCOPE =
            new ScopeContext(new LegalEntityCode("LOC"), Role.TRADER);

    @Override
    public ScopeContext requireActiveScope() {
        try {
            return RequestScopeContext.require();
        } catch (IllegalStateException ex) {
            return DEFAULT_HUB_SCOPE;
        }
    }
}
