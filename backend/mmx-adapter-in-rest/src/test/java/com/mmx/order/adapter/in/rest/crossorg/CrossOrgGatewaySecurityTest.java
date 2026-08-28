package com.mmx.order.adapter.in.rest.crossorg;

import com.mmx.order.adapter.in.rest.mapper.CrossOrgRoutingRestMapper;
import com.mmx.order.application.port.in.AcceptRoutedHubOrderUseCase;
import com.mmx.order.application.port.out.CrossOrgCredentialBinder;
import com.mmx.order.application.port.out.CrossOrgMembershipPort;
import com.mmx.order.domain.model.LegalEntityCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Gateway-level security for cross-org routing: unknown credentials → 401, proven non-members → 403.
 * The use case is never reached on either path.
 *
 * <p>Spec: {@code order-routing} — D8 trust boundary.
 */
@Tag("fast")
@ExtendWith(MockitoExtension.class)
class CrossOrgGatewaySecurityTest {

    private static final String CREDENTIAL = "key-cgd";
    private static final LegalEntityCode PROVEN = new LegalEntityCode("CGD");
    private static final UUID ROUTING_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    CrossOrgCredentialBinder credentialBinder;

    @Mock
    CrossOrgMembershipPort membershipPort;

    @Mock
    AcceptRoutedHubOrderUseCase acceptRoutedHubOrderUseCase;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CrossOrgIdentityResolver resolver = new CrossOrgIdentityResolver(credentialBinder, membershipPort);
        CrossOrgRoutingRestMapper mapper = new CrossOrgRoutingRestMapper();
        RoutedOrderAcceptController controller =
                new RoutedOrderAcceptController(resolver, acceptRoutedHubOrderUseCase, mapper);
        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new CrossOrgExceptionHandler())
                .build();
    }

    @Test
    void missingCredential_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/cross-org/routed-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        org.mockito.Mockito.verifyNoInteractions(acceptRoutedHubOrderUseCase);
    }

    @Test
    void unknownCredential_returns401() throws Exception {
        when(credentialBinder.bindOriginatingLegalEntity("bad-key")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/cross-org/routed-orders")
                        .header("X-MMX-CrossOrg-Key", "bad-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        org.mockito.Mockito.verifyNoInteractions(acceptRoutedHubOrderUseCase);
    }

    @Test
    void provenNonMember_returns403() throws Exception {
        when(credentialBinder.bindOriginatingLegalEntity(CREDENTIAL)).thenReturn(Optional.of(PROVEN));
        when(membershipPort.isRemoteTradingClientOfThisHub(PROVEN)).thenReturn(false);

        mockMvc.perform(post("/api/v1/cross-org/routed-orders")
                        .header("X-MMX-CrossOrg-Key", CREDENTIAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        org.mockito.Mockito.verifyNoInteractions(acceptRoutedHubOrderUseCase);
    }

    private static String validBody() {
        return """
                {
                  "routingId": "%s",
                  "portfolioNumber": "LOC-EUR-001",
                  "institutionCode": "HSBC-01",
                  "originatingExternalOrderReference": "CGD-PM-1",
                  "currency": "EUR",
                  "amount": 1000000.00,
                  "valueDate": "2026-08-07",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "tenor": "3M",
                  "minimumRate": 3.25
                }
                """.formatted(ROUTING_ID);
    }
}
