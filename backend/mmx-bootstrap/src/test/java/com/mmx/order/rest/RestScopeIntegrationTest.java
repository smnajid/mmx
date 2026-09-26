package com.mmx.order.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.mmx.order.application.port.out.MmxUserRepository;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MmxUser;
import com.mmx.order.domain.model.MmxUserId;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.UserScope;
import com.mmx.order.support.RestTestInstitutions;
import com.mmx.order.support.SharedPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
@Tag("integration")

@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class RestScopeIntegrationTest extends SharedPostgresTestBase {

    private static final String PAR_TRADER = "scope-trader-par";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    MmxUserRepository mmxUserRepository;

    @BeforeEach
    void seedParTrader() {
        mmxUserRepository.save(
                MmxUser.create(
                        new MmxUserId(PAR_TRADER),
                        Set.of(new UserScope(new LegalEntityCode("PAR"), Role.TRADER))));
    }

    @Test
    void traderScopedToPar_cannotReadLocOrder_returnsNotFound() throws Exception {
        String ref = "SCOPE-LOC-" + System.nanoTime();
        HttpResponse<String> created = postJson("/api/v1/orders", termSubscribeJson(ref, "LOC"));
        assertThat(created.statusCode()).isEqualTo(201);
        String orderId = objectMapper.readTree(created.body()).path("orderId").asText();

        HttpResponse<String> detail = get("/api/v1/orders/" + orderId, PAR_TRADER);
        assertThat(detail.statusCode()).isEqualTo(404);
    }

    @Test
    void intake_withoutLegalEntityCode_returns400() throws Exception {
        String ref = "SCOPE-NO-LE-" + System.nanoTime();
        LocalDate valueDate = LocalDate.now().plusDays(10);
        String json =
                """
                {
                  "externalOrderReference": "%s",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "portfolioNumber": "PF-SCOPE",
                  "currency": "EUR",
                  "amount": 1000000.00,
                  "valueDate": "%s",
                  "tenor": "3M",
                  "institutionCode": "%s"
                }
                """
                        .formatted(ref, valueDate, RestTestInstitutions.BANKCO_CODE);
        HttpResponse<String> res = postJson("/api/v1/orders", json);
        assertThat(res.statusCode()).isEqualTo(400);
    }

    @Test
    void intake_withUnknownLegalEntityCode_returns400() throws Exception {
        String ref = "SCOPE-UNK-" + System.nanoTime();
        HttpResponse<String> res = postJson("/api/v1/orders", termSubscribeJson(ref, "ZZZ"));
        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(res.body()).path("message").asText()).contains("Unknown legalEntityCode");
    }

    @Test
    void legacyXTraderIdHeader_isRejected() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri("/api/v1/orders/term/received"))
                        .timeout(Duration.ofSeconds(30))
                        .header("X-Trader-Id", "legacy-trader")
                        .GET()
                        .build();
        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(res.statusCode()).isEqualTo(401);
        assertThat(objectMapper.readTree(res.body()).path("message").asText()).contains("X-User-Id");
    }

    private HttpResponse<String> postJson(String path, String json) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
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

    private static String termSubscribeJson(String externalOrderReference, String legalEntityCode) {
        LocalDate valueDate = LocalDate.now().plusDays(10);
        return """
                {
                  "externalOrderReference": "%s",
                  "legalEntityCode": "%s",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "portfolioNumber": "PF-SCOPE",
                  "currency": "EUR",
                  "amount": 1000000.00,
                  "valueDate": "%s",
                  "tenor": "3M",
                  "institutionCode": "%s"
                }
                """
                .formatted(externalOrderReference, legalEntityCode, valueDate, RestTestInstitutions.BANKCO_CODE);
    }
}
