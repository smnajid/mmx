package com.mmx.order.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.application.port.out.InstitutionExportOutbox.ChangeReason;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.Institution;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Serialises an institution's full exported state as {@code InstitutionUpdatedV1}
 * ({@code contracts/004-institution-settings/schemas/InstitutionUpdatedV1.json}). Accounts are always present
 * (null when unset); the hub link is present only for a client-onboarded institution.
 */
@Component
public class InstitutionUpdatedV1PayloadMapper {

    static final String EVENT_TYPE = "InstitutionUpdatedV1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String toJsonPayload(Institution institution, ChangeReason reason, UUID eventId, Instant occurredAt) {
        CounterpartyAccounts accounts = institution.getCounterpartyAccounts();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventId", eventId.toString());
        map.put("eventType", EVENT_TYPE);
        map.put("legalEntityCode", institution.getOwningLegalEntityCode().value());
        map.put("institutionCode", institution.getInstitutionCode());
        map.put("displayName", institution.getDisplayName());
        institution.getHubLink().ifPresent(link -> {
            map.put("hubLegalEntityCode", link.hubLegalEntityCode().value());
            map.put("hubInstitutionCode", link.hubInstitutionCode());
        });
        map.put("termCounterpartyAccount", accounts.term().orElse(null));
        map.put("onCallCounterpartyAccount", accounts.onCall().orElse(null));
        map.put("closedToNewBusiness", institution.isClosedToNewBusiness());
        map.put("changeReason", reason.name());
        map.put("version", institution.getVersion());
        map.put("occurredAt", occurredAt.toString());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize InstitutionUpdatedV1", e);
        }
    }
}
