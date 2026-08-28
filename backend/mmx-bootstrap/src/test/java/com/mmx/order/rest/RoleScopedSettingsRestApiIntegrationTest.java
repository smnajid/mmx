package com.mmx.order.rest;

import com.mmx.order.MmxApplication;
import com.mmx.order.support.SharedPostgresTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thin REST contract smoke for role-scoped settings authorisation wiring (spec
 * {@code test-feedback-loop}, Phase B). The underlying grant/onboarding business rules are asserted
 * in fast {@code mmx-application} tests ({@code ManageDelegatedGrantsServiceTest},
 * {@code OnboardInstitutionServiceTest}); this class pins only the HTTP authorisation wiring
 * (ClientRepresentative is barred from mutating desk/settings-vs-scope surfaces) against the full
 * Spring context + PostgreSQL.
 */
@Tag("integration")
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class RoleScopedSettingsRestApiIntegrationTest extends SharedPostgresTestBase {

    private static final String DEMO_TRADER = "demo-trader";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int port;

    @Test
    void clientRepresentative_currencyOnboard_returns403() throws Exception {
        reScopeToParClient();
        HttpResponse<String> res =
                postJson(
                        "/api/v1/settings/currencies",
                        """
                        {
                          "code": "CHF",
                          "minSubscriptionAmount": 1000000.00,
                          "minIncreaseDecreaseAmount": 250000.00,
                          "enabledTenors": ["3M"],
                          "enabledNoticePeriods": ["24H"]
                        }
                        """);
        assertThat(res.statusCode()).isEqualTo(403);
    }

    @Test
    void clientRepresentative_nativeOnboardByDisplayName_returns403() throws Exception {
        reScopeToParClient();
        HttpResponse<String> res =
                postJson(
                        "/api/v1/settings/institutions",
                        """
                        {"displayName":"Free-form Native"}
                        """);
        assertThat(res.statusCode()).isEqualTo(403);
    }

    private void reScopeToParClient() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri("/api/v1/session/scope"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("X-User-Id", DEMO_TRADER)
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        """
                                        {"legalEntityCode":"PAR","role":"CLIENT_REPRESENTATIVE"}
                                        """,
                                        StandardCharsets.UTF_8))
                        .build();
        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(res.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> postJson(String path, String json) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("X-User-Id", DEMO_TRADER)
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI baseUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}