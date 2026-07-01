package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.generated.api.SessionApi;
import com.mmx.order.adapter.in.rest.generated.model.ReScopeRequest;
import com.mmx.order.adapter.in.rest.generated.model.ReScopeResponse;
import com.mmx.order.adapter.in.rest.generated.model.UserRole;
import com.mmx.order.application.port.in.ReScopeUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MmxUserId;
import com.mmx.order.domain.model.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionScopeController implements SessionApi {

    private final ReScopeUseCase reScopeUseCase;

    public SessionScopeController(ReScopeUseCase reScopeUseCase) {
        this.reScopeUseCase = reScopeUseCase;
    }

    @Override
    public ResponseEntity<ReScopeResponse> reScopeSession(String xUserId, ReScopeRequest reScopeRequest) {
        ScopeContext scope =
                reScopeUseCase.reScope(
                        new MmxUserId(xUserId),
                        new LegalEntityCode(reScopeRequest.getLegalEntityCode()),
                        mapRole(reScopeRequest.getRole()));
        return ResponseEntity.ok(
                new ReScopeResponse()
                        .legalEntityCode(scope.legalEntityCode().value())
                        .role(mapRole(scope.role())));
    }

    private static Role mapRole(UserRole role) {
        return Role.valueOf(role.name());
    }

    private static UserRole mapRole(Role role) {
        return UserRole.fromValue(role.name());
    }
}
