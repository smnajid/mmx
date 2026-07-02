package com.mmx.order.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.mmx.order.support.RestTestInstitutions;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class RoutedExecutionHandoffKafkaIntegrationTest {

    private static final String DEMO_TRADER = "demo-trader";
    private static final String SCHEMA_PATH = "/contracts/OrderExecutedV1.json";

    private static volatile JsonSchema orderExecutedPayloadSchema;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.1"));

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Value("${mmx.backoffice.kafka.topic}")
    private String backOfficeExecutedTopic;

    String proxyInstitutionCode;

    @DynamicPropertySource
    static void registerContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("mmx.backoffice.outbox.relay-enabled", () -> "true");
        registry.add("mmx.backoffice.outbox.poll-interval-ms", () -> "100");
    }

    @BeforeEach
    void seedRoutingPrerequisites() throws Exception {
        createGrantAsTrader();
        proxyInstitutionCode = onboardProxyAsParClient();
        upsertGlobalAccountAsTrader("PAR-EUR-001");
    }

    @Test
    void routedHubExecute_writesSingleOutboxRow_clientSideSuppressed() throws Exception {
        RoutedOrders routed = routeParOrder();

        reScopeToLocTrader();
        assertThat(postEmptyWithTrader("/api/v1/orders/" + routed.hubOrderId() + "/assign").statusCode())
                .isEqualTo(200);
        assertThat(
                        postJson(
                                        "/api/v1/orders/" + routed.hubOrderId() + "/execute",
                                        RestTestInstitutions.bankCoExecuteJson(3.5),
                                        DEMO_TRADER)
                                .statusCode())
                .isEqualTo(200);

        String clientStatus =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM money_market_order WHERE id = ?::uuid",
                        String.class,
                        routed.clientOrderId());
        assertThat(clientStatus).isEqualTo("EXECUTED");

        assertThat(outboxRowCount(UUID.fromString(routed.clientOrderId()))).isZero();
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(outboxRowCount(UUID.fromString(routed.hubOrderId())))
                                        .isEqualTo(1));
    }

    @Test
    void routedHubExecute_relay_publishesRoutingContextKeyedByHubOrderId() throws Exception {
        JsonSchema payloadSchema = orderExecutedPayloadSchema();
        RoutedOrders routed = routeParOrder();

        reScopeToLocTrader();
        assertThat(postEmptyWithTrader("/api/v1/orders/" + routed.hubOrderId() + "/assign").statusCode())
                .isEqualTo(200);
        assertThat(
                        postJson(
                                        "/api/v1/orders/" + routed.hubOrderId() + "/execute",
                                        RestTestInstitutions.bankCoExecuteJson(3.5),
                                        DEMO_TRADER)
                                .statusCode())
                .isEqualTo(200);

        UUID hubUuid = UUID.fromString(routed.hubOrderId());
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(outboxRowCount(hubUuid)).isEqualTo(1));

        String storedPayload =
                jdbcTemplate.queryForObject(
                        "SELECT payload FROM back_office_outbox WHERE order_id = ?::uuid",
                        String.class,
                        hubUuid);
        JsonNode payloadNode = objectMapper.readTree(storedPayload);
        assertPayloadValidates(payloadSchema, payloadNode);
        assertThat(payloadNode.path("orderId").asText()).isEqualTo(routed.hubOrderId());
        assertThat(payloadNode.path("routingId").asText()).isEqualTo(routed.routingId());
        assertThat(payloadNode.path("originatingLegalEntityCode").asText()).isEqualTo("PAR");
        assertThat(payloadNode.path("clientOrderId").asText()).isEqualTo(routed.clientOrderId());
        assertThat(payloadNode.path("clientPortfolioNumber").asText()).isEqualTo("PAR-PM-77");
        assertThat(payloadNode.path("clientCounterparty").asText()).contains("via LOC");

        await().atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofMillis(150))
                .until(() -> outboxStatus(hubUuid).equals("SENT"));

        Properties consumerProps = kafkaConsumerProps(KAFKA.getBootstrapServers());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(backOfficeExecutedTopic));
            ConsumerRecord<String, String> record =
                    await().atMost(Duration.ofSeconds(45))
                            .pollInterval(Duration.ofMillis(200))
                            .until(
                                    () -> pollForRecord(consumer, routed.hubOrderId()),
                                    Objects::nonNull);
            assertThat(record.key()).isEqualTo(routed.hubOrderId());
            assertThat(record.value()).isEqualTo(storedPayload);
            assertPayloadValidates(payloadSchema, objectMapper.readTree(record.value()));
        }
    }

    @Test
    void globalAccountChange_keepsHistoricalHubPortfolio() throws Exception {
        RoutedOrders first = routeParOrder();
        String hubPortfolioBefore =
                jdbcTemplate.queryForObject(
                        "SELECT portfolio_number FROM money_market_order WHERE id = ?::uuid",
                        String.class,
                        first.hubOrderId());
        assertThat(hubPortfolioBefore).isEqualTo("PAR-EUR-001");

        upsertGlobalAccountAsTrader("PAR-EUR-002");

        RoutedOrders second = routeParOrder();
        String secondHubPortfolio =
                jdbcTemplate.queryForObject(
                        "SELECT portfolio_number FROM money_market_order WHERE id = ?::uuid",
                        String.class,
                        second.hubOrderId());
        assertThat(secondHubPortfolio).isEqualTo("PAR-EUR-002");

        String firstHubPortfolioAfter =
                jdbcTemplate.queryForObject(
                        "SELECT portfolio_number FROM money_market_order WHERE id = ?::uuid",
                        String.class,
                        first.hubOrderId());
        assertThat(firstHubPortfolioAfter).isEqualTo("PAR-EUR-001");
    }

    private record RoutedOrders(String clientOrderId, String hubOrderId, String routingId) {}

    private RoutedOrders routeParOrder() throws Exception {
        reScopeToParClient();
        String ref = "IT-ROUTE-EXEC-" + System.nanoTime();
        HttpResponse<String> res = postJson("/api/v1/orders", parTermSubscribeJson(ref, proxyInstitutionCode));
        assertThat(res.statusCode()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(res.body());
        assertThat(body.path("status").asText()).isEqualTo("ROUTED");
        String clientOrderId = body.path("orderId").asText();

        String routingId =
                jdbcTemplate.queryForObject(
                        "SELECT routing_id::text FROM money_market_order WHERE id = ?::uuid",
                        String.class,
                        UUID.fromString(clientOrderId));
        String hubOrderId =
                jdbcTemplate.queryForObject(
                        "SELECT id::text FROM money_market_order WHERE routing_id = ?::uuid AND originating_legal_entity_code IS NOT NULL",
                        String.class,
                        UUID.fromString(routingId));
        return new RoutedOrders(clientOrderId, hubOrderId, routingId);
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

    private void upsertGlobalAccountAsTrader(String accountRef) throws Exception {
        reScopeToLocTrader();
        HttpResponse<String> res =
                putJson(
                        "/api/v1/settings/global-accounts",
                        """
                        {
                          "clientLegalEntityCode": "PAR",
                          "currency": "EUR",
                          "accountRef": "%s"
                        }
                        """
                                .formatted(accountRef));
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

    private int outboxRowCount(UUID orderId) {
        Integer cnt =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM back_office_outbox WHERE order_id = ?::uuid", Integer.class, orderId);
        return Objects.requireNonNullElse(cnt, 0);
    }

    private String outboxStatus(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM back_office_outbox WHERE order_id = ?::uuid", String.class, orderId);
    }

    private static Properties kafkaConsumerProps(String bootstrap) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "routed-handoff-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put("enable.auto.commit", "true");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return props;
    }

    private ConsumerRecord<String, String> pollForRecord(
            KafkaConsumer<String, String> consumer, String orderIdKey) {
        ConsumerRecords<String, String> batch = consumer.poll(Duration.ofMillis(600));
        for (ConsumerRecord<String, String> r : batch) {
            if (orderIdKey.equals(r.key())) {
                return r;
            }
        }
        return null;
    }

    private static JsonSchema orderExecutedPayloadSchema() {
        JsonSchema local = orderExecutedPayloadSchema;
        if (local != null) {
            return local;
        }
        synchronized (RoutedExecutionHandoffKafkaIntegrationTest.class) {
            if (orderExecutedPayloadSchema != null) {
                return orderExecutedPayloadSchema;
            }
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            try (InputStream in =
                    RoutedExecutionHandoffKafkaIntegrationTest.class.getResourceAsStream(SCHEMA_PATH)) {
                Objects.requireNonNull(in, "Missing test resource " + SCHEMA_PATH);
                orderExecutedPayloadSchema = factory.getSchema(in);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load contract JSON Schema", e);
            }
            return orderExecutedPayloadSchema;
        }
    }

    private static void assertPayloadValidates(JsonSchema schema, JsonNode payloadNode) {
        Set<ValidationMessage> errors = schema.validate(payloadNode);
        assertThat(errors)
                .as(() -> errors.stream().map(ValidationMessage::getMessage).toList().toString())
                .isEmpty();
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

    private HttpResponse<String> postJson(String path, String json, String traderId) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("X-User-Id", traderId)
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> postEmptyWithTrader(String path) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("X-User-Id", DEMO_TRADER)
                        .POST(HttpRequest.BodyPublishers.noBody())
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
