package com.mmx.order.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.mmx.order.domain.model.ExecutionDetails;
import com.mmx.order.domain.model.MoneyMarketOrder;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OrderExecutedV1PayloadMapper {

    private static final String EVENT_TYPE = "OrderExecutedV1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderExecutedV1PayloadMapper() {
        objectMapper.findAndRegisterModules();
    }

    public String toJsonPayload(MoneyMarketOrder order) {
        ExecutionDetails ex = order.getExecutionDetails();
        if (ex == null) {
            throw new IllegalStateException("Order must have execution details for handoff payload");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventType", EVENT_TYPE);
        map.put("orderId", order.getId().toString());
        map.put("executedAt", ex.executionTime().toString());
        map.put("orderType", order.getOrderType().name());
        map.put("orderOperation", order.getOrderOperation().name());
        map.put("portfolioNumber", order.getPortfolioNumber().value());
        map.put("currency", order.getCurrency());
        map.put("amount", order.getAmount().doubleValue());
        map.put("valueDate", order.getValueDate().toString());
        map.put("executedRate", ex.executedRate().doubleValue());
        map.put("counterparty", ex.counterparty());
        map.put("dealingReference", ex.dealingReference().value());
        map.put("contractNumber", ex.generatedContractNumber().value());
        map.put("externalOrderReference", order.getExternalOrderReference().value());
        map.put("tenor", order.getTenor() != null ? order.getTenor().getCode() : null);
        map.put(
                "noticePeriod",
                order.getNoticePeriod() != null ? order.getNoticePeriod().getCode() : null);

        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize OrderExecutedV1", e);
        }
    }
}
