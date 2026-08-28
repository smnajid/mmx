package com.mmx.order.adapter.in.rest.crossorg;

import com.mmx.order.adapter.in.rest.generated.crossorg.api.CrossOrgRoutingApi;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.AcceptRoutedOrderRequest;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.RoutedOrderAcceptResponse;
import com.mmx.order.adapter.in.rest.mapper.CrossOrgRoutingRestMapper;
import com.mmx.order.application.port.in.AcceptRoutedHubOrderUseCase;
import com.mmx.order.application.port.out.RemoteRoutingRequest;
import com.mmx.order.application.port.out.RemoteRoutingResponse;
import com.mmx.order.domain.model.LegalEntityCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@RestController
@ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "hub")
public class RoutedOrderAcceptController implements CrossOrgRoutingApi {

    static final String CREDENTIAL_HEADER = "X-MMX-CrossOrg-Key";

    private final CrossOrgIdentityResolver identityResolver;
    private final AcceptRoutedHubOrderUseCase acceptRoutedHubOrderUseCase;
    private final CrossOrgRoutingRestMapper mapper;

    public RoutedOrderAcceptController(
            CrossOrgIdentityResolver identityResolver,
            AcceptRoutedHubOrderUseCase acceptRoutedHubOrderUseCase,
            CrossOrgRoutingRestMapper mapper) {
        this.identityResolver = identityResolver;
        this.acceptRoutedHubOrderUseCase = acceptRoutedHubOrderUseCase;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<RoutedOrderAcceptResponse> acceptRoutedOrder(
            @Valid @RequestBody AcceptRoutedOrderRequest request) {
        LegalEntityCode proven = identityResolver.resolve(extractCredential());
        RemoteRoutingRequest remoteRequest = mapper.toRemoteRoutingRequest(request, proven);
        RemoteRoutingResponse response = acceptRoutedHubOrderUseCase.accept(remoteRequest, proven);
        UUID routingId = request.getRoutingId();
        if (response.isAccepted()) {
            return ResponseEntity.ok(mapper.toAcceptResponse(proven, routingId, response.asAccept()));
        }
        @SuppressWarnings("unchecked")
        ResponseEntity<RoutedOrderAcceptResponse> reject =
                (ResponseEntity<RoutedOrderAcceptResponse>)
                        (ResponseEntity<?>) ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .body(mapper.toRejectResponse(proven, routingId, response.asReject()));
        return reject;
    }

    private String extractCredential() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attrs.getRequest();
        return request.getHeader(CREDENTIAL_HEADER);
    }
}
