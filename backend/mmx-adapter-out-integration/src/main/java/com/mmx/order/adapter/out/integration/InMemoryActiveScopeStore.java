package com.mmx.order.adapter.out.integration;

import com.mmx.order.application.port.out.ActiveScopeStore;
import com.mmx.order.domain.model.MmxUserId;
import com.mmx.order.domain.model.UserScope;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryActiveScopeStore implements ActiveScopeStore {

    private final Map<MmxUserId, UserScope> activeScopes = new ConcurrentHashMap<>();

    @Override
    public Optional<UserScope> getActiveScope(MmxUserId userId) {
        return Optional.ofNullable(activeScopes.get(userId));
    }

    @Override
    public void setActiveScope(MmxUserId userId, UserScope scope) {
        activeScopes.put(userId, scope);
    }
}
