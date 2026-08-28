package com.mmx.order.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.mmx.order.support.RestTestInstitutions;
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
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thin REST contract smoke for the order intake / read / back-office routes (spec
 * {@code test-feedback-loop}, Phase B). Business rules (lifecycle transitions, validation, tenancy
 * scoping, queue-filter semantics) are asserted in fast {@code mmx-application}/{@code mmx-domain}
 * tests; this class pins only route reachability, status codes, and the JSON/error-mapping wiring
 * per {@code contracts/002-trader-orders-views/openapi.yaml}. Lives in {@code mmx-bootstrap} so the
 * full Spring context + Flyway-backed persistence are available.
 */
@Tag("integration")
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class OrderRestApiIntegrationTest {

    private static final String TRADER = "trader-it-1";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void postReceive_returns201_forNewOrder_wireFormat() throws Exception {
        String ref = "IT-NEW-" + System.nanoTime();
        HttpResponse<String> res = postJson("/api/v1/orders", termSubscribeJson(ref));
        assertThat(res.statusCode()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(res.body());
        assertThat(body.path("status").asText()).isEqualTo("RECEIVED");
        assertThat(body.path("orderId").asText()).isNotBlank();
    }

    @Test
    void postReceive_duplicateExternalReference_isIdempotent() throws Exception {
        String ref = "IT-DUP-" + System.nanoTime();
        String json = termSubscribeJson(ref);
        assertThat(postJson("/api/v1/orders", json).statusCode()).isEqualTo(201);
        HttpResponse<String> second = postJson("/api/v1/orders", json);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(second.body()).path("status").asText()).isEqualTo("RECEIVED");
    }

    @Test
    void postReceive_invalidPayload_returns400ErrorEnvelope() throws Exception {
        HttpResponse<String> res = postJson("/api/v1/orders", "{ \"orderType\": \"TERM\" }");
        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(res.body()).path("error").asText()).isNotBlank();
    }

    @Test
    void backOfficeAccounted_unknownOrder_returns404ErrorEnvelope() throws Exception {
        String fakeId = "00000000-0000-0000-0000-000000000099";
        HttpResponse<String> bo =
                postJsonBackOffice("/api/v1/back-office/orders/" + fakeId + "/accounted", "{}");
        assertThat(bo.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(bo.body()).path("error").asText()).isEqualTo("ORDER_NOT_FOUND");
    }

    private HttpResponse<String> postJsonBackOffice(String path, String json) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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

    private URI baseUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String termSubscribeJson(String externalOrderReference) {
        LocalDate valueDate = LocalDate.now().plusDays(10);
        return """
                {
                  "externalOrderReference": "%s",
                  "legalEntityCode": "LOC",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "portfolioNumber": "PF-IT",
                  "currency": "EUR",
                  "amount": 1000000.00,
                  "valueDate": "%s",
                  "minimumRate": 3.25,
                  "tenor": "3M",
                  "institutionCode": "%s"
                }
                """
                .formatted(externalOrderReference, valueDate, RestTestInstitutions.BANKCO_CODE);
    }
}