package com.mmx.order.support;

import org.testcontainers.utility.DockerImageName;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Shared singleton Kafka broker for {@code e2e}-tagged test classes in {@code mmx-bootstrap}
 * (spec {@code test-feedback-loop}: one broker lifecycle per JVM instead of broker-per-class).
 *
 * <p>Isolation: each e2e class registers its own DISTINCT topic name against this broker
 * (topic-per-class isolation), so leftover records from one class are never consumed by another.
 */
public final class SharedKafkaTestBroker {

    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.1"));

    static {
        try {
            KAFKA.start();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start shared Kafka broker "
                    + "(Docker must be available to run e2e tests)", e);
        }
    }

    private SharedKafkaTestBroker() {}

    public static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }
}