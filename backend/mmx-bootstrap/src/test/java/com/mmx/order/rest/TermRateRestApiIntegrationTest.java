package com.mmx.order.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.mmx.order.support.RestTestInstitutions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class TermRateRestApiIntegrationTest {

    private static final String TRADER = "trader-it-term-rates";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void uploadAndListTermRates_roundTrip() throws Exception {
        int outboxBefore = countOutboxRows();
        String csv =
                """
                tradingDate,institutionCode,currency,tenor,rate
                2026-05-30,%s,EUR,1M,3.25000000
                """
                        .formatted(RestTestInstitutions.BANKCO_CODE);

        HttpResponse<String> upload =
                multipartUpload("/api/v1/settings/term-rates/upload", "rates.csv", csv);
        assertThat(upload.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(upload.body());
        assertThat(body.path("rowCount").asInt()).isEqualTo(1);
        assertThat(body.path("tradingDate").asText()).isEqualTo("2026-05-30");

        HttpResponse<String> list =
                get("/api/v1/settings/term-rates?tradingDate=2026-05-30");
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(list.body())).hasSize(1);

        assertThat(countOutboxRows()).isEqualTo(outboxBefore);
    }

    @Test
    void upload_rejectsMixedTradingDates() throws Exception {
        String csv =
                """
                tradingDate,institutionCode,currency,tenor,rate
                2026-05-30,%s,EUR,1M,3.25000000
                2026-05-31,%s,EUR,3M,3.41000000
                """
                        .formatted(RestTestInstitutions.BANKCO_CODE, RestTestInstitutions.BANKCO_CODE);

        HttpResponse<String> res = multipartUpload("/api/v1/settings/term-rates/upload", "bad.csv", csv);
        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(res.body()).path("error").asText())
                .isEqualTo("TERM_RATE_STRUCTURAL_ERROR");
    }

    @Test
    void upload_rejectsUnknownInstitution() throws Exception {
        String csv =
                """
                tradingDate,institutionCode,currency,tenor,rate
                2026-05-30,NOPE-01,EUR,1M,3.25000000
                """;

        HttpResponse<String> res = multipartUpload("/api/v1/settings/term-rates/upload", "bad.csv", csv);
        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(res.body()).path("errors").isArray()).isTrue();
    }

    @Test
    void sampleDownload_returnsCsvAttachment() throws Exception {
        HttpResponse<String> res = get("/api/v1/settings/term-rates/sample");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("content-type").orElse("")).contains("text/csv");
        assertThat(res.headers().firstValue("content-disposition").orElse(""))
                .contains("term-rates-sample.csv");
        assertThat(res.body()).startsWith("tradingDate,institutionCode,currency,tenor,rate");
    }

    @Test
    void reupload_replacesWholeDay() throws Exception {
        String day = "2026-06-01";
        String first =
                """
                tradingDate,institutionCode,currency,tenor,rate
                %s,%s,EUR,1M,3.25000000
                %s,%s,EUR,3M,3.41000000
                """
                        .formatted(
                                day,
                                RestTestInstitutions.BANKCO_CODE,
                                day,
                                RestTestInstitutions.BANKCO_CODE);
        assertThat(multipartUpload("/api/v1/settings/term-rates/upload", "a.csv", first).statusCode())
                .isEqualTo(200);

        String second =
                """
                tradingDate,institutionCode,currency,tenor,rate
                %s,%s,EUR,1W,4.12000000
                """
                        .formatted(day, RestTestInstitutions.BANKCO_CODE);
        assertThat(multipartUpload("/api/v1/settings/term-rates/upload", "b.csv", second).statusCode())
                .isEqualTo(200);

        HttpResponse<String> list = get("/api/v1/settings/term-rates?tradingDate=" + day);
        JsonNode rows = objectMapper.readTree(list.body());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).path("tenor").asText()).isEqualTo("1W");
    }

    private int countOutboxRows() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM back_office_outbox", Integer.class);
        return count == null ? 0 : count;
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .header("X-User-Id", TRADER)
                        .GET()
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> multipartUpload(String path, String filename, String csvBody) throws Exception {
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
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .header("X-User-Id", TRADER)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
