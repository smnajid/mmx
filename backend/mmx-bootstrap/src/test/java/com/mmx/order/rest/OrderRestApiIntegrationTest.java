package com.mmx.order.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
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
 * REST contract tests for intake and read endpoints (T050). Lives in {@code mmx-bootstrap} so the full
 * Spring context and Flyway-backed persistence are available; {@code mmx-adapter-in-rest} cannot depend on the bootstrap module.
 */
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class OrderRestApiIntegrationTest {

    private static final String TRADER = "trader-it-1";
    private static final String TRADER_OTHER = "trader-it-2";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void postReceive_returns201_forNewOrder() throws Exception {
        String ref = "IT-NEW-" + System.nanoTime();
        HttpResponse<String> res = postJson("/api/v1/orders", termSubscribeJson(ref));
        assertThat(res.statusCode()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(res.body());
        assertThat(body.path("status").asText()).isEqualTo("RECEIVED");
        assertThat(body.path("orderId").asText()).isNotBlank();
    }

    @Test
    void postReceive_returns200_forDuplicateExternalReference() throws Exception {
        String ref = "IT-DUP-" + System.nanoTime();
        String json = termSubscribeJson(ref);
        assertThat(postJson("/api/v1/orders", json).statusCode()).isEqualTo(201);
        HttpResponse<String> second = postJson("/api/v1/orders", json);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(second.body()).path("status").asText()).isEqualTo("RECEIVED");
    }

    @Test
    void postReceive_returns400_forInvalidPayload() throws Exception {
        HttpResponse<String> res = postJson("/api/v1/orders", "{ \"orderType\": \"TERM\" }");
        assertThat(res.statusCode()).isEqualTo(400);
    }

    @Test
    void postReceive_returns201_whenMinimumRateOmitted() throws Exception {
        String ref = "IT-NOMIN-" + System.nanoTime();
        HttpResponse<String> res = postJson("/api/v1/orders", termSubscribeJsonWithoutMinimum(ref));
        assertThat(res.statusCode()).isEqualTo(201);
    }

    @Test
    void getTermReceived_listsOnlyTermOrders() throws Exception {
        String termRef = "IT-TERM-" + System.nanoTime();
        String onCallRef = "IT-OC-" + System.nanoTime();
        assertThat(postJson("/api/v1/orders", termSubscribeJson(termRef)).statusCode()).isEqualTo(201);
        assertThat(postJson("/api/v1/orders", onCallSubscribeJson(onCallRef)).statusCode()).isEqualTo(201);

        HttpResponse<String> res = get("/api/v1/orders/term/received?receivedView=ALL", TRADER);
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode root = objectMapper.readTree(res.body());
        assertThat(root.path("content").isArray()).isTrue();
        assertThat(root.path("content")).isNotEmpty();
        for (JsonNode item : root.path("content")) {
            assertThat(item.path("orderType").asText()).isEqualTo("TERM");
        }
    }

    @Test
    void getOnCallReceived_listsOnlyOnCallOrders() throws Exception {
        String termRef = "IT-TERM2-" + System.nanoTime();
        String onCallRef = "IT-OC2-" + System.nanoTime();
        postJson("/api/v1/orders", termSubscribeJson(termRef));
        postJson("/api/v1/orders", onCallSubscribeJson(onCallRef));

        HttpResponse<String> res = get("/api/v1/orders/oncall/received?receivedView=ALL", TRADER);
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode root = objectMapper.readTree(res.body());
        for (JsonNode item : root.path("content")) {
            assertThat(item.path("orderType").asText()).isEqualTo("ON_CALL");
        }
    }

    @Test
    void getTermReceived_nearTerm_excludesFarValueDate_allShowsThem() throws Exception {
        String ref = "IT-FAR-" + System.nanoTime();
        HttpResponse<String> created = postJson("/api/v1/orders", termSubscribeJson(ref));
        assertThat(created.statusCode()).isEqualTo(201);
        String orderId = objectMapper.readTree(created.body()).path("orderId").asText();

        HttpResponse<String> narrow = get("/api/v1/orders/term/received", TRADER);
        assertThat(narrow.statusCode()).isEqualTo(200);
        JsonNode narrowRoot = objectMapper.readTree(narrow.body());
        assertThat(contentHasOrderId(narrowRoot.path("content"), orderId)).isFalse();

        HttpResponse<String> all = get("/api/v1/orders/term/received?receivedView=ALL", TRADER);
        assertThat(all.statusCode()).isEqualTo(200);
        JsonNode allRoot = objectMapper.readTree(all.body());
        assertThat(contentHasOrderId(allRoot.path("content"), orderId)).isTrue();
    }

    @Test
    void getOrderDetails_returns200_whenExists() throws Exception {
        String ref = "IT-DET-" + System.nanoTime();
        HttpResponse<String> created = postJson("/api/v1/orders", termSubscribeJson(ref));
        assertThat(created.statusCode()).isEqualTo(201);
        String orderId = objectMapper.readTree(created.body()).path("orderId").asText();

        HttpResponse<String> res = get("/api/v1/orders/" + orderId, TRADER);
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(res.body());
        assertThat(body.path("orderId").asText()).isEqualTo(orderId);
        assertThat(body.path("externalOrderReference").asText()).isEqualTo(ref);
    }

    @Test
    void getTermAssigned_listsDeskWideAssignedTermOrdersOnly() throws Exception {
        HttpResponse<String> termPost = postJson("/api/v1/orders", termSubscribeJson("IT-TA-" + System.nanoTime()));
        HttpResponse<String> ocPost = postJson("/api/v1/orders", onCallSubscribeJson("IT-OA-" + System.nanoTime()));
        String tid = objectMapper.readTree(termPost.body()).path("orderId").asText();
        String oid = objectMapper.readTree(ocPost.body()).path("orderId").asText();

        assertThat(postEmpty("/api/v1/orders/" + tid + "/assign", TRADER).statusCode()).isEqualTo(200);
        assertThat(postEmpty("/api/v1/orders/" + oid + "/assign", TRADER).statusCode()).isEqualTo(200);

        HttpResponse<String> res = get("/api/v1/orders/term/assigned", TRADER);
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode root = objectMapper.readTree(res.body());
        for (JsonNode item : root.path("content")) {
            assertThat(item.path("orderType").asText()).isEqualTo("TERM");
        }
    }

    @Test
    void getOnCallAssigned_listsDeskWideAssignedOnCallOrdersOnly() throws Exception {
        HttpResponse<String> termPost = postJson("/api/v1/orders", termSubscribeJson("IT-TB-" + System.nanoTime()));
        HttpResponse<String> ocPost = postJson("/api/v1/orders", onCallSubscribeJson("IT-OB-" + System.nanoTime()));
        String tid = objectMapper.readTree(termPost.body()).path("orderId").asText();
        String oid = objectMapper.readTree(ocPost.body()).path("orderId").asText();

        assertThat(postEmpty("/api/v1/orders/" + tid + "/assign", TRADER).statusCode()).isEqualTo(200);
        assertThat(postEmpty("/api/v1/orders/" + oid + "/assign", TRADER).statusCode()).isEqualTo(200);

        HttpResponse<String> res = get("/api/v1/orders/oncall/assigned", TRADER);
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode root = objectMapper.readTree(res.body());
        for (JsonNode item : root.path("content")) {
            assertThat(item.path("orderType").asText()).isEqualTo("ON_CALL");
        }
    }

    @Test
    void getTermAssigned_sameOrdersRegardlessOfTraderHeader() throws Exception {
        HttpResponse<String> termPost = postJson("/api/v1/orders", termSubscribeJson("IT-TWIN-" + System.nanoTime()));
        String orderId = objectMapper.readTree(termPost.body()).path("orderId").asText();
        assertThat(postEmpty("/api/v1/orders/" + orderId + "/assign", TRADER).statusCode()).isEqualTo(200);

        JsonNode asTrader1 = objectMapper.readTree(get("/api/v1/orders/term/assigned", TRADER).body());
        JsonNode asTrader2 = objectMapper.readTree(get("/api/v1/orders/term/assigned", TRADER_OTHER).body());

        assertThat(contentHasOrderId(asTrader1.path("content"), orderId)).isTrue();
        assertThat(contentHasOrderId(asTrader2.path("content"), orderId)).isTrue();
    }

    @Test
    void getTermExecuted_listsOnlyExecutedTermOrders() throws Exception {
        HttpResponse<String> termPost = postJson("/api/v1/orders", termSubscribeJson("IT-TE-" + System.nanoTime()));
        String orderId = objectMapper.readTree(termPost.body()).path("orderId").asText();
        assertThat(postEmpty("/api/v1/orders/" + orderId + "/assign", TRADER).statusCode()).isEqualTo(200);

        HttpResponse<String> exec =
                postJson(
                        "/api/v1/orders/" + orderId + "/execute",
                        "{\"executedRate\":3.5,\"counterparty\":\"BankCo International\"}",
                        TRADER);
        assertThat(exec.statusCode()).isEqualTo(200);

        HttpResponse<String> res = get("/api/v1/orders/term/executed", TRADER);
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode root = objectMapper.readTree(res.body());
        boolean seen = false;
        for (JsonNode item : root.path("content")) {
            assertThat(item.path("orderType").asText()).isEqualTo("TERM");
            if (item.path("orderId").asText().equals(orderId)) {
                seen = true;
            }
        }
        assertThat(seen).isTrue();
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

    private HttpResponse<String> postJson(String path, String json, String traderId) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("X-Trader-Id", traderId)
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> postEmpty(String path, String traderId) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("X-Trader-Id", traderId)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path, String traderId) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("X-Trader-Id", traderId)
                        .GET()
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI baseUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static boolean contentHasOrderId(JsonNode contentArray, String orderId) {
        for (JsonNode item : contentArray) {
            if (orderId.equals(item.path("orderId").asText())) {
                return true;
            }
        }
        return false;
    }

    private static String termSubscribeJson(String externalOrderReference) {
        LocalDate valueDate = LocalDate.now().plusDays(10);
        return """
                {
                  "externalOrderReference": "%s",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "portfolioNumber": "PF-IT",
                  "currency": "EUR",
                  "amount": 1000000.00,
                  "valueDate": "%s",
                  "minimumRate": 3.25,
                  "tenor": "3M"
                }
                """
                .formatted(externalOrderReference, valueDate);
    }

    private static String onCallSubscribeJson(String externalOrderReference) {
        LocalDate valueDate = LocalDate.now().plusDays(10);
        return """
                {
                  "externalOrderReference": "%s",
                  "orderType": "ON_CALL",
                  "orderOperation": "SUBSCRIPTION",
                  "portfolioNumber": "PF-IT",
                  "currency": "EUR",
                  "amount": 500000.00,
                  "valueDate": "%s",
                  "minimumRate": 2.50,
                  "noticePeriod": "24H"
                }
                """
                .formatted(externalOrderReference, valueDate);
    }

    private static String termSubscribeJsonWithoutMinimum(String externalOrderReference) {
        LocalDate valueDate = LocalDate.now().plusDays(10);
        return """
                {
                  "externalOrderReference": "%s",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "portfolioNumber": "PF-IT",
                  "currency": "EUR",
                  "amount": 1000000.00,
                  "valueDate": "%s",
                  "tenor": "3M"
                }
                """
                .formatted(externalOrderReference, valueDate);
    }
}
