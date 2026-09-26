package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.UnauthorizedUserException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class MmxUser {

    private final MmxUserId id;
    private final Set<UserScope> scopes;
    private UserScope activeScope;

    private MmxUser(MmxUserId id, Set<UserScope> scopes, UserScope activeScope) {
        this.id = Objects.requireNonNull(id);
        this.scopes = Set.copyOf(scopes);
        this.activeScope = activeScope;
    }

    public static MmxUser create(MmxUserId id, Set<UserScope> scopes) {
        return new MmxUser(id, scopes, null);
    }

    public static MmxUser withActiveScope(MmxUserId id, Set<UserScope> scopes, UserScope activeScope) {
        MmxUser user = new MmxUser(id, scopes, null);
        return user.reScope(activeScope);
    }

    public MmxUserId getId() {
        return id;
    }

    public Set<UserScope> getScopes() {
        return scopes;
    }

    public UserScope getActiveScope() {
        return activeScope;
    }

    public boolean holdsScope(UserScope scope) {
        return scopes.contains(scope);
    }

    public boolean isAuthorised() {
        return !scopes.isEmpty() && activeScope != null;
    }

    public MmxUser reScope(UserScope requestedScope) {
        if (!holdsScope(requestedScope)) {
            throw new UnauthorizedUserException(
                    "User does not hold scope " + requestedScope.legalEntityCode() + "/" + requestedScope.role());
        }
        this.activeScope = requestedScope;
        return this;
    }

    public MmxUser withAddedScope(UserScope scope) {
        Set<UserScope> updated = new HashSet<>(scopes);
        updated.add(scope);
        return new MmxUser(id, Collections.unmodifiableSet(updated), activeScope);
    }
}
