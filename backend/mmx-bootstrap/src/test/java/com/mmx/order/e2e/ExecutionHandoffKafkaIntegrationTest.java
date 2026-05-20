package com.mmx.order.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

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

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Task 7.1: execute via HTTP against PostgreSQL (Testcontainers), assert transactional outbox payload
 * validates the AsyncAPI OrderExecutedV1 JSON Schema mirror, then assert the relay publishes the same
 * bytes to Kafka (Testcontainers) with record key = orderId.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class ExecutionHandoffKafkaIntegrationTest {

    private static final String TRADER = "trader-handoff-it-1";
    private static final String SCHEMA_PATH = "/contracts/OrderExecutedV1.json";

    private static volatile JsonSchema orderExecutedPayloadSchema;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.1"));

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${mmx.backoffice.kafka.topic}")
    private String backOfficeExecutedTopic;

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

    @Test
    void execute_writesOutbox_relay_publishesPayloadToKafka_alignedWithContractSchema()
            throws Exception {
        JsonSchema payloadSchema = orderExecutedPayloadSchema();

        String externalRef = "IT-HANDOFF-" + System.nanoTime();
        HttpResponse<String> receive = postJson("/api/v1/orders", termSubscribeJson(externalRef));
        assertThat(receive.statusCode()).isEqualTo(201);
        JsonNode receivedBody = objectMapper.readTree(receive.body());
        String orderId = receivedBody.path("orderId").asText();
        assertThat(orderId).isNotBlank();
        UUID orderUuid = UUID.fromString(orderId);

        HttpResponse<String> assigned = postEmptyWithTrader("/api/v1/orders/" + orderId + "/assign");
        assertThat(assigned.statusCode()).isEqualTo(200);

        HttpResponse<String> executed =
                postJson(
                        "/api/v1/orders/" + orderId + "/execute",
                        "{\"executedRate\":3.5,\"counterparty\":\"BankCo International\"}",
                        TRADER);
        assertThat(executed.statusCode()).isEqualTo(200);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() ->
                        assertThat(outboxPayloadRowCount(orderUuid))
                                .as("outbox row exists after EXECUTED commits")
                                .isEqualTo(1));

        String storedPayload =
                jdbcTemplate.queryForObject(
                        "SELECT payload FROM back_office_outbox WHERE order_id = ?", String.class, orderUuid);
        assertThat(storedPayload).isNotBlank();
        JsonNode payloadNode = objectMapper.readTree(storedPayload);
        assertPayloadValidates(payloadSchema, payloadNode);
        assertThat(payloadNode.path("orderId").asText()).isEqualTo(orderId);
        assertThat(payloadNode.path("externalOrderReference").asText()).isEqualTo(externalRef);

        await().atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofMillis(150))
                .until(() -> outboxStatus(orderUuid).equals("SENT"));

        String persistedPayloadAfterSend =
                jdbcTemplate.queryForObject(
                        "SELECT payload FROM back_office_outbox WHERE order_id = ?", String.class, orderUuid);
        assertThat(persistedPayloadAfterSend).isEqualTo(storedPayload);

        Properties consumerProps = kafkaConsumerProps(KAFKA.getBootstrapServers());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(backOfficeExecutedTopic));
            ConsumerRecord<String, String> record =
                    await().atMost(Duration.ofSeconds(45))
                            .pollInterval(Duration.ofMillis(200))
                            .until(() -> pollForRecord(consumer, orderId), Objects::nonNull);
            assertThat(record.key()).isEqualTo(orderId);
            assertThat(record.value()).isEqualTo(persistedPayloadAfterSend);
            assertPayloadValidates(payloadSchema, objectMapper.readTree(record.value()));
        }
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

    private int outboxPayloadRowCount(UUID orderId) {
        Integer cnt =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM back_office_outbox WHERE order_id = ?", Integer.class, orderId);
        return Objects.requireNonNullElse(cnt, 0);
    }

    private String outboxStatus(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM back_office_outbox WHERE order_id = ?", String.class, orderId);
    }

    private static Properties kafkaConsumerProps(String bootstrap) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "handoff-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put("enable.auto.commit", "true");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return props;
    }

    private static JsonSchema orderExecutedPayloadSchema() {
        JsonSchema local = orderExecutedPayloadSchema;
        if (local != null) {
            return local;
        }
        synchronized (ExecutionHandoffKafkaIntegrationTest.class) {
            if (orderExecutedPayloadSchema != null) {
                return orderExecutedPayloadSchema;
            }
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            try (InputStream in =
                    ExecutionHandoffKafkaIntegrationTest.class.getResourceAsStream(SCHEMA_PATH)) {
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
                  "portfolioNumber": "PF-HANDOFF-IT",
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
