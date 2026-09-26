package com.mmx.order.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.application.port.out.InstitutionExportOutbox.ChangeReason;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class InstitutionUpdatedV1PayloadMapperTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");
    private static final UUID EVENT_ID = UUID.fromString("7a4c2a3e-5d1b-4a9f-9a1e-3b2c1d0e9f8a");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-26T10:15:30Z");

    private final InstitutionUpdatedV1PayloadMapper mapper = new InstitutionUpdatedV1PayloadMapper();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void clientOnboardedInstitution_offboarded_carriesFullStateWithHubLinkAndNullAccount() throws Exception {
        Institution bnpViaLoc =
                Institution.onboardFromGrant(
                        "BVL-01", "BNP", new HubInstitutionLink(LOC, "BNP-01"), PAR, CounterpartyAccounts.of("PAR-BNP-T", null));
        bnpViaLoc.offboard();

        JsonNode node = json.readTree(mapper.toJsonPayload(bnpViaLoc, ChangeReason.OFFBOARDED, EVENT_ID, OCCURRED_AT));

        assertThat(node.path("eventId").asText()).isEqualTo(EVENT_ID.toString());
        assertThat(node.path("eventType").asText()).isEqualTo("InstitutionUpdatedV1");
        assertThat(node.path("legalEntityCode").asText()).isEqualTo("PAR");
        assertThat(node.path("institutionCode").asText()).isEqualTo("BVL-01");
        assertThat(node.path("displayName").asText()).isEqualTo("BNP via LOC");
        assertThat(node.path("hubLegalEntityCode").asText()).isEqualTo("LOC");
        assertThat(node.path("hubInstitutionCode").asText()).isEqualTo("BNP-01");
        assertThat(node.path("termCounterpartyAccount").asText()).isEqualTo("PAR-BNP-T");
        assertThat(node.has("onCallCounterpartyAccount")).isTrue();
        assertThat(node.path("onCallCounterpartyAccount").isNull()).isTrue();
        assertThat(node.path("closedToNewBusiness").asBoolean()).isTrue();
        assertThat(node.path("changeReason").asText()).isEqualTo("OFFBOARDED");
        assertThat(node.path("version").asLong()).isEqualTo(2);
        assertThat(node.path("occurredAt").asText()).isEqualTo("2026-09-26T10:15:30Z");
        assertValidAgainstCanonicalSchema(node);
    }

    @Test
    void hubNativeInstitution_omitsTheHubLink_andValidates() throws Exception {
        Institution hsbc =
                Institution.createNative("HSBC-01", "HSBC", LOC, CounterpartyAccounts.of("LOC-HSBC-T", "LOC-HSBC-OC"));

        JsonNode node = json.readTree(mapper.toJsonPayload(hsbc, ChangeReason.ONBOARDED, EVENT_ID, OCCURRED_AT));

        assertThat(node.has("hubLegalEntityCode")).isFalse();
        assertThat(node.has("hubInstitutionCode")).isFalse();
        assertThat(node.path("legalEntityCode").asText()).isEqualTo("LOC");
        assertThat(node.path("onCallCounterpartyAccount").asText()).isEqualTo("LOC-HSBC-OC");
        assertThat(node.path("closedToNewBusiness").asBoolean()).isFalse();
        assertThat(node.path("version").asLong()).isEqualTo(1);
        assertValidAgainstCanonicalSchema(node);
    }

    @Test
    void everyChangeReason_isAValidPayload() throws Exception {
        Institution hsbc = Institution.createNative("HSBC-01", "HSBC", LOC, CounterpartyAccounts.none());
        for (ChangeReason reason : ChangeReason.values()) {
            assertValidAgainstCanonicalSchema(json.readTree(mapper.toJsonPayload(hsbc, reason, EVENT_ID, OCCURRED_AT)));
        }
    }

    private static void assertValidAgainstCanonicalSchema(JsonNode payload) throws IOException {
        JsonSchema schema;
        try (InputStream in =
                InstitutionUpdatedV1PayloadMapperTest.class.getResourceAsStream("/contracts/InstitutionUpdatedV1.json")) {
            Objects.requireNonNull(in, "Missing canonical schema /contracts/InstitutionUpdatedV1.json");
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(in);
        }
        Set<ValidationMessage> errors = schema.validate(payload);
        assertThat(errors).as(() -> errors.toString()).isEmpty();
    }
}
