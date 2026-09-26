package com.mmx.order.adapter.in.rest.scope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.adapter.in.rest.generated.model.ErrorCode;
import com.mmx.order.adapter.in.rest.generated.model.ErrorResponse;
import com.mmx.order.application.port.in.ResolveUserScopeUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.MmxUserId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class UserScopeFilter extends OncePerRequestFilter {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String LEGACY_TRADER_HEADER = "X-Trader-Id";

    private final ResolveUserScopeUseCase resolveUserScopeUseCase;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserScopeFilter(ResolveUserScopeUseCase resolveUserScopeUseCase) {
        this.resolveUserScopeUseCase = resolveUserScopeUseCase;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/v1/orders".equals(path)) {
            return true;
        }
        if (path.startsWith("/api/v1/accounting/") || path.startsWith("/api/v1/oncall/rates/confirmation")) {
            return true;
        }
        if (!path.startsWith("/api/v1/orders")
                && !path.startsWith("/api/v1/settings")
                && !path.startsWith("/api/v1/session")
                && !path.startsWith("/api/v1/oncall/rates")) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);
        String legacyTraderId = request.getHeader(LEGACY_TRADER_HEADER);

        if ((userId == null || userId.isBlank()) && legacyTraderId != null && !legacyTraderId.isBlank()) {
            writeError(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.UNAUTHORIZED_TRADER,
                    "X-Trader-Id is no longer accepted; use X-User-Id");
            return;
        }
        if (userId == null || userId.isBlank()) {
            writeError(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.UNAUTHORIZED_TRADER,
                    "X-User-Id header is required");
            return;
        }

        try {
            ScopeContext scope = resolveUserScopeUseCase.resolve(new MmxUserId(userId));
            RequestScopeContext.set(scope);
            filterChain.doFilter(request, response);
        } catch (UnauthorizedUserException ex) {
            writeError(response, HttpStatus.FORBIDDEN, ErrorCode.UNAUTHORIZED_TRADER, ex.getMessage());
        } finally {
            RequestScopeContext.clear();
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status, ErrorCode code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse().error(code).message(message));
    }
}
