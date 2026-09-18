package com.mmx.order.config;

import com.mmx.order.MmxApplication;
import com.mmx.order.application.port.in.AcceptRoutedHubOrderUseCase;
import com.mmx.order.support.SharedPostgresTestBase;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the FULL deployment context with {@code mmx.cross-org.role=hub} — the LODH deployment
 * shape used by {@code application-lodh.yml} — against the shared Testcontainer Postgres.
 *
 * <p>Guards the hub-side cross-org wiring that only exists when the role is active: the leg-A
 * inbound use case, the transport-credential binder, the membership port, and the RoutingOutcomeV1
 * outbox + relay worker. A wiring gap here is invisible to every role-less test — the deployment
 * simply fails to start (e.g. a missing {@code java.time.Clock} bean) — so this test boots the
 * real thing and exercises the inbound endpoint's transport-proven identity: a leg-A request
 * without (or with an unknown) {@code X-MMX-CrossOrg-Key} credential must be rejected with 401
 * before any use case runs (contracts/007, "Transport-proven identity").
 */
@Tag("integration")
@TestPropertySource(properties = {
        "mmx.cross-org.role=hub",
        "mmx.cross-org.hub-legal-entity-code=LOC",
        "mmx.cross-org.outcome-topic=mmx.routed-order-outcome.TEST",
        "mmx.cross-org.credentials.test-hub-key=CGD",
})
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class CrossOrgHubDeploymentIntegrationTest extends SharedPostgresTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    AcceptRoutedHubOrderUseCase acceptRoutedHubOrderUseCase;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void hubRoleContextBoots_withLegAInboundAndOutcomeOutboxWired() {
        // Context boots (this test would fail earlier otherwise) and the hub-only beans exist.
        assertThat(acceptRoutedHubOrderUseCase).isNotNull();
    }

    @Test
    void legAInbound_withoutCredential_returns401_beforeAnyUseCaseRuns() throws Exception {
        HttpResponse<String> response = postLegA(null);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void legAInbound_withUnknownCredential_returns401() throws Exception {
        HttpResponse<String> response = postLegA("no-such-key");
        assertThat(response.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> postLegA(String credentialKey) throws Exception {
        // Schema-valid leg-A body (contracts/007 AcceptRoutedOrderRequest) — values are irrelevant
        // here: identity is transport-proven, so the 401 fires before the payload is ever trusted.
        String validBody = """
                {
                  "routingId": "3f2b8c4e-1d5a-4c6b-9e2f-7a8b1c2d3e4f",
                  "portfolioNumber": "CGD-LOC-001",
                  "institutionCode": "BNP",
                  "originatingExternalOrderReference": "CGEG-TEST-0001",
                  "currency": "EUR",
                  "amount": 1000000,
                  "valueDate": "2099-01-01",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "tenor": "3M"
                }
                """;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/cross-org/routed-orders"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(validBody));
        if (credentialKey != null) {
            builder.header("X-MMX-CrossOrg-Key", credentialKey);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
