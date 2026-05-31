package com.mmx.order.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class OnCallRateCanceledV1PayloadMapper {

    private static final String EVENT_TYPE = "OnCallRateCanceledV1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String toJsonPayload(UUID segmentId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventType", EVENT_TYPE);
        map.put("segmentId", segmentId.toString());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize OnCallRateCanceledV1", e);
        }
    }
}
