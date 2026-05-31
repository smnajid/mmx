package com.mmx.order.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OnCallRateUpdatedV1PayloadMapperTest {

    private final OnCallRateUpdatedV1PayloadMapper mapper = new OnCallRateUpdatedV1PayloadMapper();

    @Test
    void builds_contract_aligned_payload_withoutPriorEndDate() throws Exception {
        OnCallRateSegment segment =
                OnCallRateSegment.createPending(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        new OnCallCurveKey("HSBC-01", "EUR", NoticePeriod._24H),
                        new BigDecimal("3.25000000"),
                        LocalDate.of(2026, 6, 1));

        String json = mapper.toJsonPayload(segment);
        JsonNode node = new ObjectMapper().readTree(json);

        assertThat(node.path("eventType").asText()).isEqualTo("OnCallRateUpdatedV1");
        assertThat(node.path("segmentId").asText()).isEqualTo(segment.getSegmentId().toString());
        assertThat(node.path("institution").asText()).isEqualTo("HSBC-01");
        assertThat(node.path("currency").asText()).isEqualTo("EUR");
        assertThat(node.path("noticePeriod").asText()).isEqualTo("24H");
        assertThat(node.path("rate").asDouble()).isEqualTo(3.25);
        assertThat(node.path("valueDate").asText()).isEqualTo("2026-06-01");
        assertThat(node.has("priorEndDate")).isFalse();
    }
}
