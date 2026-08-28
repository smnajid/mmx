package com.mmx.order.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.mmx.order.support.RestTestInstitutions;
import com.mmx.order.support.SharedPostgresTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
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
@Tag("integration")

@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class DelegatedGrantsRestApiIntegrationTest extends SharedPostgresTestBase {

    private static final String DEMO_TRADER = "demo-trader";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void trader_createsAndListsGrant() throws Exception {
        reScopeToLocTrader();

        String createJson =
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

        HttpResponse<String> created = postJson("/api/v1/settings/delegated-grants", createJson, DEMO_TRADER);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(created.body());
        assertThat(body.path("hubInstitutionCode").asText()).isEqualTo(RestTestInstitutions.BANKCO_CODE);
        assertThat(body.path("clientLegalEntityCode").asText()).isEqualTo("PAR");
        assertThat(body.path("active").asBoolean()).isTrue();

        HttpResponse<String> list = get("/api/v1/settings/delegated-grants", DEMO_TRADER);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(list.body())).isNotEmpty();
    }

    @Test
    void clientRepresentative_grantCrud_returns403() throws Exception {
        reScopeToParClient();

        HttpResponse<String> list = get("/api/v1/settings/delegated-grants", DEMO_TRADER);
        assertThat(list.statusCode()).isEqualTo(403);

        String createJson =
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
        HttpResponse<String> created = postJson("/api/v1/settings/delegated-grants", createJson, DEMO_TRADER);
        assertThat(created.statusCode()).isEqualTo(403);
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

    private HttpResponse<String> postJson(String path, String json, String userId) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("X-User-Id", userId)
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path, String userId) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("X-User-Id", userId)
                        .GET()
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI baseUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
