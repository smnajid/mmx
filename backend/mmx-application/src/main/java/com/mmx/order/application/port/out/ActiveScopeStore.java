package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.MmxUserId;
import com.mmx.order.domain.model.UserScope;

import java.util.Optional;

/** Persists the active `(LegalEntityCode, Role)` scope bound to a user session. */
public interface ActiveScopeStore {

    Optional<UserScope> getActiveScope(MmxUserId userId);

    void setActiveScope(MmxUserId userId, UserScope scope);
}
