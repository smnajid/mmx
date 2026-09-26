package com.mmx.order.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.application.port.in.ApplyRemoteOrderOutcomeUseCase;
import com.mmx.order.application.port.in.RemoteOrderOutcome;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;
import com.mmx.order.domain.model.ExecutionDetails;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.RoutingId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Client-deployment (e.g. CGED) leg-B Kafka consumer adapter for the routed-order-outcome channel.
 * Decodes the canonical {@code RoutingOutcomeV1} payload (see
 * {@code contracts/007-cross-org-routing/schemas/RoutingOutcomeV1.json}), filters by
 * {@code originatingLegalEntityCode ∈ {this deployment's own LegalEntities}}, and delegates to
 * {@link ApplyRemoteOrderOutcomeUseCase}. The producer is blind to who is listening; the client
 * filters on consume and silently skips non-matching events (the offset still advances).
 *
 * <p>Spec: {@code back-office-outbound-messaging} — routed-order-outcome channel; spec
 * {@code order-routing} — silence is never terminal. No deployment-internal order UUID crosses the
 * boundary; the consumer correlates purely on {@code (originatingLegalEntityCode, routingId)}.
 *
 * <p>The {@code @KafkaListener} wiring (topic = {@code mmx.routed-order-outcome.<hubOrg>}, consumer
 * group, deserializer) lives in {@code mmx-bootstrap}; this adapter is a plain bean so the decode +
 * filter + delegate behaviour is unit-testable without a broker.
 */
public class RoutingOutcomeV1Consumer {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ApplyRemoteOrderOutcomeUseCase applyRemoteOrderOutcomeUseCase;
    private final Set<String> ownLegalEntityCodes;

    public RoutingOutcomeV1Consumer(
            ApplyRemoteOrderOutcomeUseCase applyRemoteOrderOutcomeUseCase, Set<String> ownLegalEntityCodes) {
        this.applyRemoteOrderOutcomeUseCase = Objects.requireNonNull(applyRemoteOrderOutcomeUseCase);
        this.ownLegalEntityCodes = Set.copyOf(Objects.requireNonNull(ownLegalEntityCodes));
    }

    public void onRoutingOutcome(String payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        JsonNode node = parse(payload);
        String originatingLegalEntityCode = text(node, "originatingLegalEntityCode");
        if (!ownLegalEntityCodes.contains(originatingLegalEntityCode)) {
            return;
        }
        applyRemoteOrderOutcomeUseCase.apply(decode(node));
    }

    private RemoteOrderOutcome decode(JsonNode node) {
        LegalEntityCode originatingLe = new LegalEntityCode(text(node, "originatingLegalEntityCode"));
        RoutingId routingId = new RoutingId(java.util.UUID.fromString(text(node, "routingId")));
        String outcomeType = text(node, "outcomeType");
        Instant occurredAt = parseInstant(node, "occurredAt");
        return switch (outcomeType) {
            case "ACCEPTED" -> new RemoteOrderOutcome.Accepted(originatingLe, routingId, occurredAt);
            case "EXECUTED" -> new RemoteOrderOutcome.Executed(
                    originatingLe, routingId, executionDetails(node, occurredAt), parseInstant(node, "executedAt"));
            case "CANCELLED" -> new RemoteOrderOutcome.Cancelled(originatingLe, routingId, occurredAt);
            case "REJECTED" -> new RemoteOrderOutcome.Rejected(
                    originatingLe, routingId, text(node, "reason"), parseInstant(node, "rejectedAt"));
            default -> throw new IllegalStateException("Unknown RoutingOutcomeV1 outcomeType: " + outcomeType);
        };
    }

    private static ExecutionDetails executionDetails(JsonNode node, Instant executionTime) {
        String institutionCode = text(node, "institutionCode");
        return new ExecutionDetails(
                new BigDecimal(node.get("executedRate").asText()),
                institutionCode,
                institutionCode,
                executionTime,
                new DealingReference(text(node, "dealingReference")),
                new ContractNumber(text(node, "contractNumber")));
    }

    private static JsonNode parse(String payload) {
        try {
            return JSON.readTree(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode RoutingOutcomeV1 payload", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            throw new IllegalStateException("RoutingOutcomeV1 missing required field: " + field);
        }
        return child.asText();
    }

    private static Instant parseInstant(JsonNode node, String field) {
        return Instant.parse(text(node, field));
    }
}
