package com.mmx.order.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.mmx.order.adapter.out.integration.InMemoryOpenPositionPort;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.OpenContractPosition;
import com.mmx.order.support.RestTestInstitutions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
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

    @Autowired
    InMemoryOpenPositionPort openPositionPort;

    @Test
    void postReceive_returns400_forOnCallLifecycleWithMismatchedInstitution() throws Exception {
        String subRef = "IT-OC-SUB-" + System.nanoTime();
        HttpResponse<String> created = postJson("/api/v1/orders", onCallSubscribeJson(subRef));
        assertThat(created.statusCode()).isEqualTo(201);
        String orderId = objectMapper.readTree(created.body()).path("orderId").asText();
        assertThat(postEmpty("/api/v1/orders/" + orderId + "/assign", TRADER).statusCode()).isEqualTo(200);
        HttpResponse<String> executed =
                postJson(
                        "/api/v1/orders/" + orderId + "/execute",
                        RestTestInstitutions.rateOnlyExecuteJson(3.5),
                        TRADER);
        assertThat(executed.statusCode()).isEqualTo(200);
        String contractNumber =
                objectMapper.readTree(executed.body()).path("generatedContractNumber").asText();

        String increaseRef = "IT-OC-INC-MISMATCH-" + System.nanoTime();
        LocalDate valueDate = LocalDate.now().plusDays(10);
        String json =
                """
                {
                  "externalOrderReference": "%s",
                  "orderType": "ON_CALL",
                  "orderOperation": "INCREASE",
                  "portfolioNumber": "PF-IT",
                  "currency": "EUR",
                  "amount": 100000.00,
                  "valueDate": "%s",
                  "noticePeriod": "24H",
                  "sourceContractNumber": "%s",
                  "institutionCode": "%s"
                }
                """
                        .formatted(
                                increaseRef,
                                valueDate,
                                contractNumber,
                                RestTestInstitutions.BANKCO_CODE);
        HttpResponse<String> res = postJson("/api/v1/orders", json);
        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(res.body()).path("message").asText())
                .contains("must match the source contract institution");
    }

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
    void postReceive_returns400_forUnmanagedCurrency() throws Exception {
        String ref = "IT-JPY-" + System.nanoTime();
        LocalDate valueDate = LocalDate.now().plusDays(10);
        String json =
                """
                {
                  "externalOrderReference": "%s",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "portfolioNumber": "PF-IT",
                  "currency": "JPY",
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
    void postReceive_returns400_whenAmountBelowMinimum() throws Exception {
        String ref = "IT-BELOW-MIN-" + System.nanoTime();
        LocalDate valueDate = LocalDate.now().plusDays(10);
        String json =
                """
                {
                  "externalOrderReference": "%s",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "portfolioNumber": "PF-IT",
                  "currency": "EUR",
                  "amount": 0.50,
                  "valueDate": "%s",
                  "tenor": "3M",
                  "institutionCode": "%s"
                }
                """
                        .formatted(ref, valueDate, RestTestInstitutions.BANKCO_CODE);
        assertThat(postJson("/api/v1/orders", json).statusCode()).isEqualTo(400);
    }

    @Test
    void postReceive_returns400_forDisabledTenor() throws Exception {
        HttpResponse<String> onboard =
                postJson("/api/v1/settings/currencies", onboardSekOnly1mJson(), TRADER);
        assertThat(onboard.statusCode()).isIn(201, 409);
        if (onboard.statusCode() == 409) {
            HttpResponse<String> patch =
                    patchJson(
                            "/api/v1/settings/currencies/SEK",
                            """
                            {
                              "enabledTenors": ["1M"],
                              "enabledNoticePeriods": ["24H"]
                            }
                            """,
                            TRADER);
            assertThat(patch.statusCode()).isEqualTo(200);
        }

        String ref = "IT-DIS-TENOR-" + System.nanoTime();
        LocalDate valueDate = LocalDate.now().plusDays(10);
        String json =
                """
                {
                  "externalOrderReference": "%s",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "portfolioNumber": "PF-IT",
                  "currency": "SEK",
                  "amount": 1000000.00,
                  "valueDate": "%s",
                  "tenor": "3M",
                  "institutionCode": "%s"
                }
                """
                        .formatted(ref, valueDate, RestTestInstitutions.BANKCO_CODE);
        assertThat(postJson("/api/v1/orders", json).statusCode()).isEqualTo(400);
    }

    @Test
    void postReceive_returns400_forDecreaseBelowSubscriptionFloor() throws Exception {
        ensureGbpOnboarded();
        openPositionPort.register(
                new OpenContractPosition(new ContractNumber("CNT-IT-FLOOR"), "GBP", new BigDecimal("150.00")));
        String ref = "IT-DEC-FLOOR-" + System.nanoTime();
        LocalDate valueDate = LocalDate.now().plusDays(10);
        String json =
                """
                {
                  "externalOrderReference": "%s",
                  "orderType": "ON_CALL",
                  "orderOperation": "DECREASE",
                  "portfolioNumber": "PF-IT",
                  "currency": "GBP",
                  "amount": 60.00,
                  "valueDate": "%s",
                  "sourceContractNumber": "CNT-IT-FLOOR",
                  "noticePeriod": "24H",
                  "institutionCode": "%s"
                }
                """
                        .formatted(ref, valueDate, RestTestInstitutions.CP_OC_CODE);
        assertThat(postJson("/api/v1/orders", json).statusCode()).isEqualTo(400);
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
                        RestTestInstitutions.bankCoExecuteJson(3.5),
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

    @Test
    void getTermExecuted_rowsIncludeCounterpartyAndExcludeOnCallWorkspace() throws Exception {
        String termRef = "IT-TEC-" + System.nanoTime();
        String onRef = "IT-OEC-" + System.nanoTime();
        HttpResponse<String> termPost = postJson("/api/v1/orders", termSubscribeJson(termRef));
        HttpResponse<String> ocPost = postJson("/api/v1/orders", onCallSubscribeJson(onRef));
        assertThat(termPost.statusCode()).isEqualTo(201);
        assertThat(ocPost.statusCode()).isEqualTo(201);
        String termId = objectMapper.readTree(termPost.body()).path("orderId").asText();
        String onId = objectMapper.readTree(ocPost.body()).path("orderId").asText();
        assertThat(postEmpty("/api/v1/orders/" + termId + "/assign", TRADER).statusCode()).isEqualTo(200);
        assertThat(postEmpty("/api/v1/orders/" + onId + "/assign", TRADER).statusCode()).isEqualTo(200);
        String execPayload = RestTestInstitutions.bankCoExecuteJson(3.5);
        assertThat(postJson("/api/v1/orders/" + termId + "/execute", execPayload, TRADER).statusCode()).isEqualTo(200);
        assertThat(postJson("/api/v1/orders/" + onId + "/execute", execPayload, TRADER).statusCode()).isEqualTo(200);

        HttpResponse<String> res = get("/api/v1/orders/term/executed", TRADER);
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode root = objectMapper.readTree(res.body());
        boolean termSeen = false;
        for (JsonNode item : root.path("content")) {
            assertThat(item.path("orderType").asText()).isEqualTo("TERM");
            assertThat(item.path("status").asText()).isEqualTo("EXECUTED");
            assertThat(onId.equals(item.path("orderId").asText())).isFalse();
            if (termId.equals(item.path("orderId").asText())) {
                assertThat(item.path("counterparty").asText()).isEqualTo("BankCo International");
                termSeen = true;
            }
        }
        assertThat(termSeen).isTrue();
    }

    @Test
    void getOnCallExecuted_rowsIncludeCounterpartyAndExcludeTermWorkspace() throws Exception {
        String termRef = "IT-TE2-" + System.nanoTime();
        String onRef = "IT-OC2-" + System.nanoTime();
        HttpResponse<String> termPost = postJson("/api/v1/orders", termSubscribeJson(termRef));
        HttpResponse<String> ocPost = postJson("/api/v1/orders", onCallSubscribeJson(onRef));
        String termId = objectMapper.readTree(termPost.body()).path("orderId").asText();
        String onId = objectMapper.readTree(ocPost.body()).path("orderId").asText();
        postEmpty("/api/v1/orders/" + termId + "/assign", TRADER);
        postEmpty("/api/v1/orders/" + onId + "/assign", TRADER);
        String execPayload = RestTestInstitutions.rateOnlyExecuteJson(3.5);
        postJson("/api/v1/orders/" + termId + "/execute", execPayload, TRADER);
        postJson("/api/v1/orders/" + onId + "/execute", execPayload, TRADER);

        HttpResponse<String> res = get("/api/v1/orders/oncall/executed", TRADER);
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode root = objectMapper.readTree(res.body());
        boolean onSeen = false;
        for (JsonNode item : root.path("content")) {
            assertThat(item.path("orderType").asText()).isEqualTo("ON_CALL");
            assertThat(item.path("status").asText()).isEqualTo("EXECUTED");
            assertThat(termId.equals(item.path("orderId").asText())).isFalse();
            if (onId.equals(item.path("orderId").asText())) {
                assertThat(item.path("counterparty").asText()).isEqualTo("CP-OC");
                onSeen = true;
            }
        }
        assertThat(onSeen).isTrue();
    }

    @Test
    void postBackOfficeAccounted_transitionsExecutedToAccounted() throws Exception {
        HttpResponse<String> termPost = postJson("/api/v1/orders", termSubscribeJson("IT-BOA-" + System.nanoTime()));
        String orderId = objectMapper.readTree(termPost.body()).path("orderId").asText();
        assertThat(postEmpty("/api/v1/orders/" + orderId + "/assign", TRADER).statusCode()).isEqualTo(200);
        assertThat(
                        postJson(
                                        "/api/v1/orders/" + orderId + "/execute",
                                        RestTestInstitutions.bankCoExecuteJson(3.5),
                                        TRADER)
                                .statusCode())
                .isEqualTo(200);

        HttpResponse<String> bo = postJsonBackOffice("/api/v1/back-office/orders/" + orderId + "/accounted", "{}");
        assertThat(bo.statusCode()).isEqualTo(200);

        HttpResponse<String> detail = get("/api/v1/orders/" + orderId, TRADER);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(detail.body()).path("status").asText()).isEqualTo("ACCOUNTED");

        HttpResponse<String> executedList = get("/api/v1/orders/term/executed", TRADER);
        assertThat(executedList.statusCode()).isEqualTo(200);
        assertThat(contentHasOrderId(objectMapper.readTree(executedList.body()).path("content"), orderId))
                .isFalse();
    }

    @Test
    void postBackOfficeAccounted_unknownOrder_returns404() throws Exception {
        String fakeId = "00000000-0000-0000-0000-000000000099";
        HttpResponse<String> bo = postJsonBackOffice("/api/v1/back-office/orders/" + fakeId + "/accounted", "{}");
        assertThat(bo.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(bo.body()).path("error").asText()).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void postBackOfficeAccounted_nonExecuted_returns409() throws Exception {
        HttpResponse<String> termPost = postJson("/api/v1/orders", termSubscribeJson("IT-BO409-" + System.nanoTime()));
        String orderId = objectMapper.readTree(termPost.body()).path("orderId").asText();
        HttpResponse<String> bo = postJsonBackOffice("/api/v1/back-office/orders/" + orderId + "/accounted", "{}");
        assertThat(bo.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(bo.body()).path("error").asText()).isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void postBackOfficeAccounted_idempotent_secondCall_leavesUpdatedAt() throws Exception {
        HttpResponse<String> termPost = postJson("/api/v1/orders", termSubscribeJson("IT-BOI-" + System.nanoTime()));
        String orderId = objectMapper.readTree(termPost.body()).path("orderId").asText();
        postEmpty("/api/v1/orders/" + orderId + "/assign", TRADER);
        postJson(
                "/api/v1/orders/" + orderId + "/execute",
                RestTestInstitutions.bankCoExecuteJson(3.5),
                TRADER);

        assertThat(postJsonBackOffice("/api/v1/back-office/orders/" + orderId + "/accounted", "{}").statusCode())
                .isEqualTo(200);
        String updatedAt1 = objectMapper.readTree(get("/api/v1/orders/" + orderId, TRADER).body())
                .path("updatedAt")
                .asText();

        assertThat(postJsonBackOffice("/api/v1/back-office/orders/" + orderId + "/accounted", "{}").statusCode())
                .isEqualTo(200);
        String updatedAt2 = objectMapper.readTree(get("/api/v1/orders/" + orderId, TRADER).body())
                .path("updatedAt")
                .asText();

        assertThat(updatedAt2).isEqualTo(updatedAt1);
    }

    @Test
    void postBackOfficeAccounted_reachableWithoutTraderHeader() throws Exception {
        HttpResponse<String> termPost = postJson("/api/v1/orders", termSubscribeJson("IT-BONH-" + System.nanoTime()));
        String orderId = objectMapper.readTree(termPost.body()).path("orderId").asText();
        postEmpty("/api/v1/orders/" + orderId + "/assign", TRADER);
        postJson(
                "/api/v1/orders/" + orderId + "/execute",
                RestTestInstitutions.bankCoExecuteJson(3.5),
                TRADER);

        HttpResponse<String> bo = postJsonBackOffice("/api/v1/back-office/orders/" + orderId + "/accounted", "{}");
        assertThat(bo.statusCode()).isEqualTo(200);
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

    private void ensureGbpOnboarded() throws Exception {
        HttpResponse<String> onboard = postJson("/api/v1/settings/currencies", onboardGbpJson(), TRADER);
        assertThat(onboard.statusCode()).isIn(201, 409);
    }

    private static String onboardGbpJson() {
        return """
                {
                  "code": "GBP",
                  "minSubscriptionAmount": 100.00,
                  "minIncreaseDecreaseAmount": 1.00,
                  "enabledTenors": ["1M", "3M"],
                  "enabledNoticePeriods": ["24H"]
                }
                """;
    }

    private static String onboardSekOnly1mJson() {
        return """
                {
                  "code": "SEK",
                  "minSubscriptionAmount": 1.00,
                  "minIncreaseDecreaseAmount": 1.00,
                  "enabledTenors": ["1M"],
                  "enabledNoticePeriods": ["24H"]
                }
                """;
    }

    private HttpResponse<String> patchJson(String path, String json, String traderId) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("X-Trader-Id", traderId)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
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
                  "tenor": "3M",
                  "institutionCode": "%s"
                }
                """
                .formatted(externalOrderReference, valueDate, RestTestInstitutions.BANKCO_CODE);
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
                  "noticePeriod": "24H",
                  "institutionCode": "%s"
                }
                """
                .formatted(externalOrderReference, valueDate, RestTestInstitutions.CP_OC_CODE);
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
                  "tenor": "3M",
                  "institutionCode": "%s"
                }
                """
                .formatted(externalOrderReference, valueDate, RestTestInstitutions.BANKCO_CODE);
    }
}
