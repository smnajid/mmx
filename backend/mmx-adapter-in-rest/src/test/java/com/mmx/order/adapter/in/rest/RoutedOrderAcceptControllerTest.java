package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.crossorg.CrossOrgExceptionHandler;
import com.mmx.order.adapter.in.rest.crossorg.CrossOrgIdentityResolver;
import com.mmx.order.adapter.in.rest.crossorg.RoutedOrderAcceptController;
import com.mmx.order.adapter.in.rest.mapper.CrossOrgRoutingRestMapper;
import com.mmx.order.application.port.in.AcceptRoutedHubOrderUseCase;
import com.mmx.order.application.port.out.CrossOrgCredentialBinder;
import com.mmx.order.application.port.out.CrossOrgMembershipPort;
import com.mmx.order.application.port.out.RemoteRoutingRequest;
import com.mmx.order.application.port.out.RemoteRoutingResponse;
import com.mmx.order.domain.model.LegalEntityCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
@Tag("fast")

@ExtendWith(MockitoExtension.class)
class RoutedOrderAcceptControllerTest {

    private static final String CREDENTIAL = "key-cgd";
    private static final LegalEntityCode PROVEN = new LegalEntityCode("CGD");
    private static final UUID ROUTING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

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
        lenient().when(membershipPort.isRemoteTradingClientOfThisHub(PROVEN)).thenReturn(true);
    }

    @Test
    void acceptRoutedOrder_bindsCredentialToProvenLeAndReturns200OnAccept() throws Exception {
        when(credentialBinder.bindOriginatingLegalEntity(CREDENTIAL)).thenReturn(Optional.of(PROVEN));
        Instant acceptedAt = Instant.parse("2026-08-02T12:00:00Z");
        when(acceptRoutedHubOrderUseCase.accept(any(RemoteRoutingRequest.class), eq(PROVEN)))
                .thenReturn(new RemoteRoutingResponse.Accept(acceptedAt));

        mockMvc.perform(post("/api/v1/cross-org/routed-orders")
                        .header("X-MMX-CrossOrg-Key", CREDENTIAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTermSubscriptionBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.originatingLegalEntityCode").value("CGD"))
                .andExpect(jsonPath("$.routingId").value(ROUTING_ID.toString()))
                .andExpect(jsonPath("$.acceptedAt").exists());

        verify(acceptRoutedHubOrderUseCase).accept(any(RemoteRoutingRequest.class), eq(PROVEN));
    }

    @Test
    void acceptRoutedOrder_returns422OnReject() throws Exception {
        when(credentialBinder.bindOriginatingLegalEntity(CREDENTIAL)).thenReturn(Optional.of(PROVEN));
        when(acceptRoutedHubOrderUseCase.accept(any(RemoteRoutingRequest.class), eq(PROVEN)))
                .thenReturn(new RemoteRoutingResponse.Reject("Grant validation failed"));

        mockMvc.perform(post("/api/v1/cross-org/routed-orders")
                        .header("X-MMX-CrossOrg-Key", CREDENTIAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTermSubscriptionBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.originatingLegalEntityCode").value("CGD"))
                .andExpect(jsonPath("$.routingId").value(ROUTING_ID.toString()))
                .andExpect(jsonPath("$.reason").value("Grant validation failed"));
    }

    private static String validTermSubscriptionBody() {
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
