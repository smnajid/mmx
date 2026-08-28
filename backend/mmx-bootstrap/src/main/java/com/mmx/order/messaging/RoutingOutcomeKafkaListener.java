package com.mmx.order.messaging;

import com.mmx.order.adapter.out.messaging.RoutingOutcomeV1Consumer;

import org.springframework.kafka.annotation.KafkaListener;

/**
 * Thin Spring {@code @KafkaListener} shell over the framework-free {@link RoutingOutcomeV1Consumer}.
 *
 * <p>The decode + filter + delegate logic lives in the consumer (unit-tested without a broker); this
 * listener only adapts the Kafka record value to the consumer's {@code onRoutingOutcome(String)}
 * entry point. Registered only on the client deployment (role=client) against the hub-owned,
 * org-suffixed leg-B topic {@code mmx.routed-order-outcome.<hubOrg>} under a consume-only ACL.
 *
 * <p>Spec: {@code back-office-outbound-messaging} — routed-order-outcome channel.
 */
public class RoutingOutcomeKafkaListener {

    private final RoutingOutcomeV1Consumer consumer;

    public RoutingOutcomeKafkaListener(RoutingOutcomeV1Consumer consumer) {
        this.consumer = consumer;
    }

    @KafkaListener(
            topics = "${mmx.cross-org.outcome-topic:mmx.routed-order-outcome.unconfigured}",
            groupId = "${mmx.cross-org.consumer-group:mmx-cged-routed-outcome}",
            autoStartup = "${mmx.cross-org.consumer-enabled:false}")
    public void onMessage(String payload) {
        consumer.onRoutingOutcome(payload);
    }
}
