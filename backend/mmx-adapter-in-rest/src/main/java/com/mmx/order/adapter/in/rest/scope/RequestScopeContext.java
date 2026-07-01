package com.mmx.order.adapter.in.rest.scope;

import com.mmx.order.application.port.in.ScopeContext;

public final class RequestScopeContext {

    private static final ThreadLocal<ScopeContext> CURRENT = new ThreadLocal<>();

    private RequestScopeContext() {}

    public static void set(ScopeContext scope) {
        CURRENT.set(scope);
    }

    public static ScopeContext require() {
        ScopeContext scope = CURRENT.get();
        if (scope == null) {
            throw new IllegalStateException("No active ScopeContext on request thread");
        }
        return scope;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
