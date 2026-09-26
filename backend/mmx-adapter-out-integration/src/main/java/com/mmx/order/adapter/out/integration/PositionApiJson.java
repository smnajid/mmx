package com.mmx.order.adapter.out.integration;

import com.fasterxml.jackson.databind.ObjectMapper;

final class PositionApiJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PositionApiJson() {}

    static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception ex) {
            return null;
        }
    }
}
