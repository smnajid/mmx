package com.mmx.order.adapter.out.persistence;

import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.Role;

/** Fixed scope for persistence integration tests and non-request threads. */
public class FixedScopeContextProvider implements ScopeContextProvider {

  private volatile ScopeContext scope = new ScopeContext(new LegalEntityCode("LOC"), Role.TRADER);

  public void setScope(ScopeContext scope) {
    this.scope = scope;
  }

  @Override
  public ScopeContext requireActiveScope() {
    return scope;
  }
}
