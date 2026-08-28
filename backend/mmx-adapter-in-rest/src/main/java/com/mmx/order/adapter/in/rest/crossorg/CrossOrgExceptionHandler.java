package com.mmx.order.adapter.in.rest.crossorg;

import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgErrorCode;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgErrorResponse;
import com.mmx.order.domain.exception.CrossOrgMembershipException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "hub")
public class CrossOrgExceptionHandler {

    @ExceptionHandler(UnauthorizedCrossOrgException.class)
    public ResponseEntity<CrossOrgErrorResponse> handleUnauthorized(UnauthorizedCrossOrgException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new CrossOrgErrorResponse()
                        .error(CrossOrgErrorCode.UNAUTHORIZED)
                        .message(ex.getMessage()));
    }

    @ExceptionHandler({ForbiddenCrossOrgMembershipException.class, CrossOrgMembershipException.class})
    public ResponseEntity<CrossOrgErrorResponse> handleForbidden(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new CrossOrgErrorResponse()
                        .error(CrossOrgErrorCode.FORBIDDEN)
                        .message(ex.getMessage()));
    }
}
