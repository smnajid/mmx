package com.mmx.order.application.port.out;

import com.mmx.order.application.port.in.ScopeContext;

public interface ScopeContextProvider {

    ScopeContext requireActiveScope();
}
