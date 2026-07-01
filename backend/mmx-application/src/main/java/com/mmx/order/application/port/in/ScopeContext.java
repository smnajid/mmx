package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.UserScope;

/** Active `(LegalEntityCode, Role)` scope for desk/settings reads. */
public record ScopeContext(LegalEntityCode legalEntityCode, Role role) {

    public static ScopeContext from(UserScope scope) {
        return new ScopeContext(scope.legalEntityCode(), scope.role());
    }
}
