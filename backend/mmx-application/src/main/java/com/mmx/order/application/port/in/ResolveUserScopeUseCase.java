package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.MmxUserId;

/** Resolves (or establishes) the active scope for an authenticated user. */
public interface ResolveUserScopeUseCase {

    ScopeContext resolve(MmxUserId userId);
}
