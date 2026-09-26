package com.mmx.order.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.domain.model.OnCallRateSegment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OnCallRateUpdatedV1PayloadMapper {

    private static final String EVENT_TYPE = "OnCallRateUpdatedV1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OnCallRateUpdatedV1PayloadMapper() {
        objectMapper.findAndRegisterModules();
    }

    public String toJsonPayload(OnCallRateSegment segment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventType", EVENT_TYPE);
        map.put("segmentId", segment.getSegmentId().toString());
        map.put("institution", segment.getCurveKey().institutionCode());
        map.put("currency", segment.getCurveKey().currency());
        map.put("noticePeriod", segment.getCurveKey().noticePeriod().getCode());
        map.put("rate", segment.getRate().doubleValue());
        map.put("valueDate", segment.getValueDate().toString());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize OnCallRateUpdatedV1", e);
        }
    }
}
