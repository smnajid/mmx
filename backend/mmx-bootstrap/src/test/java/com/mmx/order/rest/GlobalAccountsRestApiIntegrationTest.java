package com.mmx.order.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.mmx.order.support.SharedPostgresTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
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
class GlobalAccountsRestApiIntegrationTest extends SharedPostgresTestBase {

    private static final String DEMO_TRADER = "demo-trader";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void trader_can_upsert_and_list_global_accounts() throws Exception {
        reScopeToLocTrader();
        HttpResponse<String> upserted =
                putJson(
                        "/api/v1/settings/global-accounts",
                        """
                        {
                          "clientLegalEntityCode": "PAR",
                          "currency": "EUR",
                          "accountRef": "PAR-EUR-001"
                        }
                        """);
        assertThat(upserted.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(upserted.body()).path("accountRef").asText()).isEqualTo("PAR-EUR-001");

        HttpResponse<String> listed = get("/api/v1/settings/global-accounts");
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body()).contains("PAR-EUR-001");
    }

    @Test
    void clientRepresentative_cannot_mutate_global_accounts() throws Exception {
        reScopeToParClient();
        HttpResponse<String> res =
                putJson(
                        "/api/v1/settings/global-accounts",
                        """
                        {
                          "clientLegalEntityCode": "PAR",
                          "currency": "EUR",
                          "accountRef": "PAR-EUR-002"
                        }
                        """);
        assertThat(res.statusCode()).isEqualTo(403);
    }

    private void reScopeToLocTrader() throws Exception {
        HttpResponse<String> res =
                httpClient.send(
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
                                .build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(res.statusCode()).isEqualTo(200);
    }

    private void reScopeToParClient() throws Exception {
        HttpResponse<String> res =
                httpClient.send(
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
                                .build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(res.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("X-User-Id", DEMO_TRADER)
                        .GET()
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> putJson(String path, String json) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("X-User-Id", DEMO_TRADER)
                        .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI baseUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
