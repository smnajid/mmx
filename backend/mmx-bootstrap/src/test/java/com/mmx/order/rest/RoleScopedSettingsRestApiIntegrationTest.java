package com.mmx.order.rest;

import com.mmx.order.MmxApplication;
import com.mmx.order.support.RestTestInstitutions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class RoleScopedSettingsRestApiIntegrationTest {

    private static final String DEMO_TRADER = "demo-trader";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerPostgresProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

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
    void clientRepresentative_proxyOnboardFromGrant_succeeds() throws Exception {
        createGrantAsTrader();

        reScopeToParClient();

        HttpResponse<String> onboarded =
                postJson(
                        "/api/v1/settings/institutions",
                        """
                        {"hubInstitutionCode":"%s"}
                        """
                                .formatted(RestTestInstitutions.BANKCO_CODE));
        assertThat(onboarded.statusCode()).isEqualTo(201);
        assertThat(onboarded.body()).contains("via LOC");
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

    private void createGrantAsTrader() throws Exception {
        reScopeToLocTrader();
        String json =
                """
                {
                  "hubInstitutionCode": "%s",
                  "clientLegalEntityCode": "PAR",
                  "currency": "EUR",
                  "enabledTenors": ["3M"],
                  "enabledNoticePeriods": []
                }
                """
                        .formatted(RestTestInstitutions.BANKCO_CODE);
        HttpResponse<String> created = postJson("/api/v1/settings/delegated-grants", json);
        assertThat(created.statusCode()).isEqualTo(201);
    }

    private void reScopeToLocTrader() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri("/api/v1/session/scope"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("X-User-Id", DEMO_TRADER)
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        """
                                        {"legalEntityCode":"LOC","role":"TRADER"}
                                        """,
                                        StandardCharsets.UTF_8))
                        .build();
        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(res.statusCode()).isEqualTo(200);
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
