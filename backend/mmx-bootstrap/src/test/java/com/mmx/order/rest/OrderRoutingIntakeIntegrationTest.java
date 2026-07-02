package com.mmx.order.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.mmx.order.support.RestTestInstitutions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class OrderRoutingIntakeIntegrationTest {

    private static final String DEMO_TRADER = "demo-trader";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    String proxyInstitutionCode;

    @DynamicPropertySource
    static void registerPostgresProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void seedRoutingPrerequisites() throws Exception {
        createGrantAsTrader();
        proxyInstitutionCode = onboardProxyAsParClient();
        upsertGlobalAccountAsTrader();
    }

    @Test
    void tradingClientIntake_returnsRoutedWithLinkedHubOrder() throws Exception {
        String ref = "IT-ROUTE-" + System.nanoTime();
        HttpResponse<String> res = postJson("/api/v1/orders", parTermSubscribeJson(ref, proxyInstitutionCode));
        assertThat(res.statusCode()).isEqualTo(201);

        JsonNode body = objectMapper.readTree(res.body());
        assertThat(body.path("status").asText()).isEqualTo("ROUTED");
        UUID clientOrderId = UUID.fromString(body.path("orderId").asText());

        String routingId =
                jdbcTemplate.queryForObject(
                        "SELECT routing_id::text FROM money_market_order WHERE id = ?",
                        String.class,
                        clientOrderId);
        assertThat(routingId).isNotBlank();

        Integer hubCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM money_market_order WHERE routing_id = ?::uuid AND originating_legal_entity_code IS NOT NULL",
                        Integer.class,
                        routingId);
        assertThat(hubCount).isEqualTo(1);

        String hubStatus =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM money_market_order WHERE routing_id = ?::uuid AND originating_legal_entity_code IS NOT NULL",
                        String.class,
                        routingId);
        assertThat(hubStatus).isEqualTo("RECEIVED");
    }

    @Test
    void tradingHubIntake_returnsNativeReceived() throws Exception {
        String ref = "IT-NATIVE-" + System.nanoTime();
        HttpResponse<String> res = postJson("/api/v1/orders", locTermSubscribeJson(ref));
        assertThat(res.statusCode()).isEqualTo(201);
        assertThat(objectMapper.readTree(res.body()).path("status").asText()).isEqualTo("RECEIVED");

        UUID orderId = UUID.fromString(objectMapper.readTree(res.body()).path("orderId").asText());
        String routingId =
                jdbcTemplate.queryForObject(
                        "SELECT routing_id::text FROM money_market_order WHERE id = ?",
                        String.class,
                        orderId);
        assertThat(routingId).isNull();
    }

    private void createGrantAsTrader() throws Exception {
        reScopeToLocTrader();
        HttpResponse<String> created =
                postJson(
                        "/api/v1/settings/delegated-grants",
                        """
                        {
                          "hubInstitutionCode": "%s",
                          "clientLegalEntityCode": "PAR",
                          "currency": "EUR",
                          "enabledTenors": ["3M"],
                          "enabledNoticePeriods": []
                        }
                        """
                                .formatted(RestTestInstitutions.BANKCO_CODE));
        assertThat(created.statusCode()).isIn(201, 409);
    }

    private String onboardProxyAsParClient() throws Exception {
        reScopeToParClient();
        HttpResponse<String> onboarded =
                postJson(
                        "/api/v1/settings/institutions",
                        """
                        {"hubInstitutionCode":"%s"}
                        """
                                .formatted(RestTestInstitutions.BANKCO_CODE));
        if (onboarded.statusCode() == 201) {
            return objectMapper.readTree(onboarded.body()).path("institutionCode").asText();
        }
        assertThat(onboarded.statusCode()).isEqualTo(409);
        HttpResponse<String> listed = get("/api/v1/settings/institutions");
        assertThat(listed.statusCode()).isEqualTo(200);
        for (JsonNode node : objectMapper.readTree(listed.body())) {
            if (RestTestInstitutions.BANKCO_CODE.equals(node.path("hubInstitutionCode").asText())) {
                return node.path("institutionCode").asText();
            }
        }
        throw new IllegalStateException("Proxy institution not found after conflict");
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

    private void upsertGlobalAccountAsTrader() throws Exception {
        reScopeToLocTrader();
        HttpResponse<String> res =
                putJson(
                        "/api/v1/settings/global-accounts",
                        """
                        {
                          "clientLegalEntityCode": "PAR",
                          "currency": "EUR",
                          "accountRef": "PAR-EUR-001"
                        }
                        """);
        assertThat(res.statusCode()).isEqualTo(200);
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

    private static String parTermSubscribeJson(String externalOrderReference, String proxyCode) {
        LocalDate valueDate = LocalDate.now().plusDays(10);
        return """
                {
                  "externalOrderReference": "%s",
                  "legalEntityCode": "PAR",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "portfolioNumber": "PAR-PM-77",
                  "currency": "EUR",
                  "amount": 1000000.00,
                  "valueDate": "%s",
                  "minimumRate": 2.5,
                  "tenor": "3M",
                  "institutionCode": "%s"
                }
                """
                .formatted(externalOrderReference, valueDate, proxyCode);
    }

    private static String locTermSubscribeJson(String externalOrderReference) {
        LocalDate valueDate = LocalDate.now().plusDays(10);
        return """
                {
                  "externalOrderReference": "%s",
                  "legalEntityCode": "LOC",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "portfolioNumber": "PF-IT",
                  "currency": "EUR",
                  "amount": 5000000.00,
                  "valueDate": "%s",
                  "minimumRate": 3.25,
                  "tenor": "3M",
                  "institutionCode": "%s"
                }
                """
                .formatted(externalOrderReference, valueDate, RestTestInstitutions.BANKCO_CODE);
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
