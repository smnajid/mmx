package com.mmx.order.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.mmx.order.support.RestTestInstitutions;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class OrderCreationOptionsRestApiIntegrationTest {

    private static final String TRADER = "trader-it-order-creation";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    private LocalDate segmentStart;
    private LocalDate valueDate;

    @BeforeEach
    void setUpDates() {
        segmentStart = LocalDate.now();
        valueDate = segmentStart.plusDays(10);
    }

    @Test
    void getTermCurrencies_returnsFilteredCurrencies() throws Exception {
        uploadTermRates(
                """
                tradingDate,institutionCode,currency,tenor,rate
                %s,%s,EUR,3M,3.25000000
                """
                        .formatted(segmentStart, RestTestInstitutions.BANKCO_CODE));

        HttpResponse<String> res = getWithoutTraderHeader("/api/v1/order-creation/term/currencies");
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(res.body());
        assertThat(body.path("currencies").isArray()).isTrue();
        boolean hasEur = false;
        for (JsonNode currency : body.path("currencies")) {
            if ("EUR".equals(currency.asText())) {
                hasEur = true;
                break;
            }
        }
        assertThat(hasEur).isTrue();
    }

    @Test
    void getTermCounterparties_returnsInstitutionsSortedByBestRate() throws Exception {
        uploadTermRates(
                """
                tradingDate,institutionCode,currency,tenor,rate
                %s,%s,EUR,3M,3.25000000
                %s,%s,EUR,3M,4.10000000
                """
                        .formatted(
                                segmentStart,
                                RestTestInstitutions.BANKCO_CODE,
                                segmentStart,
                                RestTestInstitutions.CP_OC_CODE));

        HttpResponse<String> res =
                getWithoutTraderHeader(
                        "/api/v1/order-creation/term/counterparties?currency=EUR&tenor=3M");
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode counterparties = objectMapper.readTree(res.body()).path("counterparties");
        assertThat(counterparties).hasSize(2);
        assertThat(counterparties.get(0).path("institutionCode").asText())
                .isEqualTo(RestTestInstitutions.CP_OC_CODE);
        assertThat(counterparties.get(0).path("rate").asDouble()).isEqualTo(4.1);
        assertThat(counterparties.get(1).path("institutionCode").asText())
                .isEqualTo(RestTestInstitutions.BANKCO_CODE);
        assertThat(counterparties.get(1).path("rate").asDouble()).isEqualTo(3.25);
    }

    @Test
    void getOnCallCounterparties_returnsSegmentRates() throws Exception {
        addOnCallRate(RestTestInstitutions.BANKCO_CODE, "EUR", "24H", 2.5, segmentStart);
        addOnCallRate(RestTestInstitutions.CP_OC_CODE, "EUR", "24H", 3.75, segmentStart);

        HttpResponse<String> res =
                getWithoutTraderHeader(
                        "/api/v1/order-creation/oncall/counterparties?currency=EUR&noticePeriod=24H&valueDate="
                                + valueDate);
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode counterparties = objectMapper.readTree(res.body()).path("counterparties");
        assertThat(counterparties).hasSize(2);
        assertThat(counterparties.get(0).path("institutionCode").asText())
                .isEqualTo(RestTestInstitutions.CP_OC_CODE);
        assertThat(counterparties.get(0).path("rate").asDouble()).isEqualTo(3.75);
        assertThat(counterparties.get(1).path("institutionCode").asText())
                .isEqualTo(RestTestInstitutions.BANKCO_CODE);
        assertThat(counterparties.get(1).path("rate").asDouble()).isEqualTo(2.5);
    }

    @Test
    void getOnCallContractInfo_returns200ForExecutedSubscription() throws Exception {
        String ref = "IT-OC-CONTRACT-" + System.nanoTime();
        HttpResponse<String> created = postJson("/api/v1/orders", onCallSubscribeJson(ref));
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
        assertThat(contractNumber).isNotBlank();

        HttpResponse<String> info =
                getWithoutTraderHeader(
                        "/api/v1/order-creation/oncall/contract-info?contractNumber=" + contractNumber);
        assertThat(info.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(info.body());
        assertThat(body.path("currency").asText()).isEqualTo("EUR");
        assertThat(body.path("noticePeriod").asText()).isEqualTo("24H");
    }

    @Test
    void getOnCallContractInfo_returns404WhenUnknown() throws Exception {
        HttpResponse<String> res =
                getWithoutTraderHeader(
                        "/api/v1/order-creation/oncall/contract-info?contractNumber=NO-SUCH-CONTRACT");
        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(res.body()).path("error").asText()).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void orderCreationEndpoints_workWithoutTraderHeader() throws Exception {
        uploadTermRates(
                """
                tradingDate,institutionCode,currency,tenor,rate
                %s,%s,EUR,1M,3.00000000
                """
                        .formatted(segmentStart, RestTestInstitutions.BANKCO_CODE));

        assertThat(getWithoutTraderHeader("/api/v1/order-creation/term/currencies").statusCode())
                .isEqualTo(200);
        assertThat(getWithoutTraderHeader("/api/v1/order-creation/oncall/currencies").statusCode())
                .isEqualTo(200);
        assertThat(getWithoutTraderHeader("/api/v1/order-creation/term/operations?currency=EUR")
                        .statusCode())
                .isEqualTo(200);
    }

    private void uploadTermRates(String csv) throws Exception {
        HttpResponse<String> upload =
                multipartUpload("/api/v1/settings/term-rates/upload", "rates.csv", csv);
        assertThat(upload.statusCode()).isEqualTo(200);
    }

    private void addOnCallRate(
            String institutionCode, String currency, String noticePeriod, double rate, LocalDate start)
            throws Exception {
        String json =
                """
                {
                  "currency": "%s",
                  "noticePeriod": "%s",
                  "rate": %s,
                  "valueDate": "%s"
                }
                """
                        .formatted(currency, noticePeriod, rate, start);
        HttpResponse<String> res =
                postJson("/api/v1/settings/institutions/" + institutionCode + "/oncall-rates", json, TRADER);
        assertThat(res.statusCode()).isEqualTo(201);
    }

    private HttpResponse<String> getWithoutTraderHeader(String path) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
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

    private HttpResponse<String> multipartUpload(String path, String filename, String csvBody)
            throws Exception {
        String boundary = "----mmxBoundary";
        byte[] body =
                ("--"
                                + boundary
                                + "\r\n"
                                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                                + filename
                                + "\"\r\n"
                                + "Content-Type: text/csv\r\n\r\n"
                                + csvBody
                                + "\r\n--"
                                + boundary
                                + "--\r\n")
                        .getBytes(StandardCharsets.UTF_8);
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .header("X-Trader-Id", TRADER)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI baseUri(String path) {
        return URI.create("http://localhost:" + port + path);
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
}
