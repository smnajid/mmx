package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ResolveUserScopeUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.ActiveScopeStore;
import com.mmx.order.application.port.out.MmxUserRepository;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.MmxUser;
import com.mmx.order.domain.model.MmxUserId;
import com.mmx.order.domain.model.UserScope;

public final class ResolveUserScopeService implements ResolveUserScopeUseCase {

    private final MmxUserRepository mmxUserRepository;
    private final ActiveScopeStore activeScopeStore;

    public ResolveUserScopeService(MmxUserRepository mmxUserRepository, ActiveScopeStore activeScopeStore) {
        this.mmxUserRepository = mmxUserRepository;
        this.activeScopeStore = activeScopeStore;
    }

    @Override
    public ScopeContext resolve(MmxUserId userId) {
        MmxUser user =
                mmxUserRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new UnauthorizedUserException("Unknown MMXUser: " + userId.value()));
        if (user.getScopes().isEmpty()) {
            throw new UnauthorizedUserException("MMXUser has no allowed scopes: " + userId.value());
        }
        UserScope active =
                activeScopeStore
                        .getActiveScope(userId)
                        .filter(user::holdsScope)
                        .orElseGet(() -> user.getScopes().iterator().next());
        activeScopeStore.setActiveScope(userId, active);
        return ScopeContext.from(active);
    }
}
