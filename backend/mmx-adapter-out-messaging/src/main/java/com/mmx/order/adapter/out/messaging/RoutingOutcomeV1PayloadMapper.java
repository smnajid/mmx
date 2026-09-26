package com.mmx.order.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.domain.model.ExecutionDetails;
import com.mmx.order.domain.model.MoneyMarketOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps a hub-side order transition to a {@code RoutingOutcomeV1} JSON payload for leg-B publishing,
 * conforming to the canonical contract {@code contracts/007-cross-org-routing/schemas/RoutingOutcomeV1.json}.
 *
 * <p>Required on every variant: {@code eventType} (const {@code RoutingOutcomeV1}), {@code outcomeType}
 * (discriminator), {@code originatingLegalEntityCode}, {@code routingId}, {@code occurredAt}. The
 * cross-boundary correlation key {@code (originatingLegalEntityCode, routingId)} is always present; no
 * internal order UUID crosses the boundary. Variant-specific timestamps mirror {@code occurredAt}.
 */
final class RoutingOutcomeV1PayloadMapper {

    static final String EVENT_TYPE = "RoutingOutcomeV1";

    private static final ObjectMapper JSON = new ObjectMapper();

    private RoutingOutcomeV1PayloadMapper() {}

    static String accepted(MoneyMarketOrder hubOrder, Instant acceptedAt) {
        Map<String, Object> payload = basePayload("ACCEPTED", hubOrder, acceptedAt);
        payload.put("acceptedAt", acceptedAt.toString());
        return toJson(payload);
    }

    static String executed(MoneyMarketOrder hubOrder, Instant executedAt) {
        Map<String, Object> payload = basePayload("EXECUTED", hubOrder, executedAt);
        payload.put("executedAt", executedAt.toString());
        ExecutionDetails exec = hubOrder.getExecutionDetails();
        if (exec != null) {
            payload.put("executedRate", exec.executedRate() != null ? exec.executedRate().toString() : null);
            payload.put("institutionCode", exec.institutionCode());
            payload.put("dealingReference",
                    exec.dealingReference() != null ? exec.dealingReference().value() : null);
            payload.put("contractNumber",
                    exec.generatedContractNumber() != null ? exec.generatedContractNumber().value() : null);
        }
        return toJson(payload);
    }

    static String cancelled(MoneyMarketOrder hubOrder, Instant cancelledAt) {
        Map<String, Object> payload = basePayload("CANCELLED", hubOrder, cancelledAt);
        payload.put("cancelledAt", cancelledAt.toString());
        return toJson(payload);
    }

    static String rejected(MoneyMarketOrder hubOrder, String reason, Instant rejectedAt) {
        Map<String, Object> payload = basePayload("REJECTED", hubOrder, rejectedAt);
        payload.put("rejectedAt", rejectedAt.toString());
        payload.put("reason", reason);
        return toJson(payload);
    }

    private static Map<String, Object> basePayload(String outcomeType, MoneyMarketOrder hubOrder, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", EVENT_TYPE);
        payload.put("outcomeType", outcomeType);
        payload.put("originatingLegalEntityCode",
                hubOrder.getOriginatingLegalEntityCode() != null
                        ? hubOrder.getOriginatingLegalEntityCode().value()
                        : null);
        payload.put("routingId", hubOrder.getRoutingId() != null ? hubOrder.getRoutingId().value().toString() : null);
        payload.put("occurredAt", occurredAt.toString());
        return payload;
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return JSON.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize routing-outcome payload", e);
        }
    }
}
