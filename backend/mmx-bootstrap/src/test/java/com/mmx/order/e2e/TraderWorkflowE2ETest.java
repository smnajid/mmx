package com.mmx.order.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.mmx.order.support.RestTestInstitutions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 8 (T090): full Trader workflow against a real PostgreSQL instance powered by Testcontainers —
 * receive, list Term received, assign, execute, then verify EXECUTED payload (dealing reference and
 * generated contract number).
 *
 * <p>{@code disabledWithoutDocker} skips this class (no failure) when Docker is not available, so
 * {@code mvn test} stays usable on machines without the daemon.
 *
 * <p>{@code spring.datasource.driver-class-name} is set explicitly because the {@code rest-test} profile
 * pins the H2 driver while this test overrides the JDBC URL to PostgreSQL.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class TraderWorkflowE2ETest {

    private static final String TRADER = "trader-e2e-1";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerPostgresProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // rest-test profile pins H2 driver; override so Flyway/JPA use Postgres with the container URL.
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void receive_list_assign_execute_verify_fullTraderWorkflow() throws Exception {
        String externalRef = "E2E-T090-" + System.nanoTime();

        HttpResponse<String> receive = postJson("/api/v1/orders", termSubscribeJson(externalRef));
        assertThat(receive.statusCode()).isEqualTo(201);
        JsonNode receivedBody = objectMapper.readTree(receive.body());
        assertThat(receivedBody.path("status").asText()).isEqualTo("RECEIVED");
        String orderId = receivedBody.path("orderId").asText();
        assertThat(orderId).isNotBlank();

        HttpResponse<String> listTerm = get("/api/v1/orders/term/received?receivedView=ALL", TRADER);
        assertThat(listTerm.statusCode()).isEqualTo(200);
        JsonNode listBody = objectMapper.readTree(listTerm.body());
        assertThat(findOrderIdInPagedContent(listBody, orderId)).as("order appears in Term received queue").isTrue();

        HttpResponse<String> assigned = postEmptyWithTrader("/api/v1/orders/" + orderId + "/assign");
        assertThat(assigned.statusCode()).isEqualTo(200);
        JsonNode assignedBody = objectMapper.readTree(assigned.body());
        assertThat(assignedBody.path("status").asText()).isEqualTo("ASSIGNED");

        HttpResponse<String> executed = postJson(
                "/api/v1/orders/" + orderId + "/execute",
                RestTestInstitutions.bankCoExecuteJson(3.5),
                TRADER);
        assertThat(executed.statusCode()).isEqualTo(200);
        JsonNode executedBody = objectMapper.readTree(executed.body());
        assertThat(executedBody.path("status").asText()).isEqualTo("EXECUTED");
        assertThat(executedBody.path("dealingReference").asText()).isNotBlank();
        assertThat(executedBody.path("generatedContractNumber").asText()).isNotBlank();

        HttpResponse<String> detail = get("/api/v1/orders/" + orderId, TRADER);
        assertThat(detail.statusCode()).isEqualTo(200);
        JsonNode detailBody = objectMapper.readTree(detail.body());
        assertThat(detailBody.path("status").asText()).isEqualTo("EXECUTED");
        assertThat(detailBody.path("externalOrderReference").asText()).isEqualTo(externalRef);
    }

    private boolean findOrderIdInPagedContent(JsonNode root, String orderId) {
        JsonNode content = root.path("content");
        if (!content.isArray()) {
            return false;
        }
        for (JsonNode item : content) {
            if (Objects.equals(item.path("orderId").asText(), orderId)) {
                return true;
            }
        }
        return false;
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

    private HttpResponse<String> postEmptyWithTrader(String path) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("X-Trader-Id", TRADER)
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

    private static String termSubscribeJson(String externalOrderReference) {
        LocalDate valueDate = LocalDate.now().plusDays(10);
        return """
                {
                  "externalOrderReference": "%s",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "portfolioNumber": "PF-E2E",
                  "currency": "EUR",
                  "amount": 5000000.00,
                  "valueDate": "%s",
                  "minimumRate": 3.25,
                  "tenor": "3M"
                }
                """
                .formatted(externalOrderReference, valueDate);
    }
}
