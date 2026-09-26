package com.mmx.order.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.mmx.order.support.RoutedClientFixture;
import com.mmx.order.support.SharedKafkaTestBroker;
import com.mmx.order.support.SharedPostgresTestBase;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Institution export acceptance lock: onboarding at {@code PAR} commits one {@code InstitutionUpdatedV1}
 * outbox row, and the relay publishes it only to {@code mmx.institution.PAR}, keyed by
 * {@code institutionCode}, with a payload that validates against the canonical
 * {@code contracts/004-institution-settings/schemas/InstitutionUpdatedV1.json}.
 */
@Tag("e2e")
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class InstitutionExportKafkaIntegrationTest extends SharedPostgresTestBase {

    private final ObjectMapper json = new ObjectMapper();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", SharedKafkaTestBroker::bootstrapServers);
        registry.add("mmx.institution.outbox.relay-enabled", () -> "true");
        registry.add("mmx.institution.outbox.poll-interval-ms", () -> "100");
    }

    @Test
    void onboardingAtPar_publishesOneInstitutionUpdatedV1_toParsTopicOnly() throws Exception {
        RoutedClientFixture fx = new RoutedClientFixture(port);
        String hubCode = freshHubInstitutionGrantedToPar(fx);

        fx.asParClient();
        HttpResponse<String> onboarded =
                fx.post(
                        "/api/v1/settings/institutions",
                        "{\"hubInstitutionCode\":\"%s\",\"termCounterpartyAccount\":\"PAR-EXP-T\"}".formatted(hubCode));
        assertThat(onboarded.statusCode()).as(onboarded.body()).isEqualTo(201);
        String parCode = json.readTree(onboarded.body()).path("institutionCode").asText();

        List<ConsumerRecord<String, String>> parRecords = recordsKeyed("mmx.institution.PAR", parCode, 1);
        assertThat(parRecords).hasSize(1);
        JsonNode payload = json.readTree(parRecords.getFirst().value());
        assertThat(payload.path("legalEntityCode").asText()).isEqualTo("PAR");
        assertThat(payload.path("changeReason").asText()).isEqualTo("ONBOARDED");
        assertThat(payload.path("hubInstitutionCode").asText()).isEqualTo(hubCode);
        assertThat(payload.path("termCounterpartyAccount").asText()).isEqualTo("PAR-EXP-T");
        assertThat(payload.path("onCallCounterpartyAccount").isNull()).isTrue();
        assertValidAgainstCanonicalSchema(payload);

        assertThat(recordsKeyed("mmx.institution.LOC", parCode, 0))
                .as("PAR's institution state is never published to LOC's topic")
                .isEmpty();
    }

    /** A new LOC-native institution (with its own LOC export) granted to PAR in EUR; returns its code. */
    private String freshHubInstitutionGrantedToPar(RoutedClientFixture fx) throws Exception {
        fx.asLocTrader();
        HttpResponse<String> created =
                fx.post(
                        "/api/v1/settings/institutions",
                        "{\"displayName\":\"Export Bank\",\"termCounterpartyAccount\":\"LOC-EXP-T\"}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String hubCode = json.readTree(created.body()).path("institutionCode").asText();
        HttpResponse<String> granted =
                fx.post(
                        "/api/v1/settings/delegated-grants",
                        """
                        {"hubInstitutionCode":"%s","clientLegalEntityCode":"PAR","currency":"EUR",
                         "enabledTenors":["3M"],"enabledNoticePeriods":[]}
                        """
                                .formatted(hubCode));
        assertThat(granted.statusCode()).as(granted.body()).isEqualTo(201);
        return hubCode;
    }

    /**
     * Records on {@code topic} keyed {@code key}: waits until {@code expected} arrive, or, for
     * {@code expected == 0}, drains the topic for a few seconds to prove none does.
     */
    private List<ConsumerRecord<String, String>> recordsKeyed(String topic, String key, int expected) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, SharedKafkaTestBroker.bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "institution-export-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            if (expected == 0) {
                long until = System.currentTimeMillis() + 5_000;
                while (System.currentTimeMillis() < until) {
                    consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                        if (key.equals(r.key())) {
                            matching.add(r);
                        }
                    });
                }
                return matching;
            }
            await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(200)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (key.equals(r.key())) {
                        matching.add(r);
                    }
                });
                return matching.size() >= expected;
            });
            // A short extra drain so a duplicate publish would be caught.
            consumer.poll(Duration.ofSeconds(2)).forEach(r -> {
                if (key.equals(r.key())) {
                    matching.add(r);
                }
            });
        }
        return matching;
    }

    private static void assertValidAgainstCanonicalSchema(JsonNode payload) throws Exception {
        JsonSchema schema;
        try (InputStream in =
                InstitutionExportKafkaIntegrationTest.class.getResourceAsStream("/contracts/InstitutionUpdatedV1.json")) {
            Objects.requireNonNull(in, "Missing canonical schema /contracts/InstitutionUpdatedV1.json");
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(in);
        }
        Set<ValidationMessage> errors = schema.validate(payload);
        assertThat(errors).as(errors::toString).isEmpty();
    }
}
