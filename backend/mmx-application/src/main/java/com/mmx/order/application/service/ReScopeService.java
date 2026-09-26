package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ReScopeUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.ActiveScopeStore;
import com.mmx.order.application.port.out.MmxUserRepository;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MmxUser;
import com.mmx.order.domain.model.MmxUserId;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.UserScope;

public final class ReScopeService implements ReScopeUseCase {

    private final MmxUserRepository mmxUserRepository;
    private final ActiveScopeStore activeScopeStore;

    public ReScopeService(MmxUserRepository mmxUserRepository, ActiveScopeStore activeScopeStore) {
        this.mmxUserRepository = mmxUserRepository;
        this.activeScopeStore = activeScopeStore;
    }

    @Override
    public ScopeContext reScope(MmxUserId userId, LegalEntityCode legalEntityCode, Role role) {
        MmxUser user =
                mmxUserRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new UnauthorizedUserException("Unknown MMXUser: " + userId.value()));
        UserScope requested = new UserScope(legalEntityCode, role);
        user.reScope(requested);
        mmxUserRepository.save(user);
        activeScopeStore.setActiveScope(userId, requested);
        return ScopeContext.from(requested);
    }
}
