package com.mmx.order.adapter.out.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mmx.order.application.port.out.HubInstitutionCatalog;
import com.mmx.order.domain.model.Institution;

import java.util.List;
import java.util.Optional;

/**
 * Remote-backed {@link HubInstitutionCatalog}: a remote TradingClient reads its hub's native institutions
 * live from the hub deployment ({@code /cross-org/reference/institutions}). It is the source of hub display
 * names for the granted-institution list and for onboarding. Read-only: the client stores only its own
 * onboarded institutions (local JPA), never hub reference data.
 */
public final class RemoteHubInstitutionCatalog implements HubInstitutionCatalog {

    private static final String PATH = "/api/v1/cross-org/reference/institutions";

    private final RemoteReferenceDataContext ctx;

    public RemoteHubInstitutionCatalog(RemoteReferenceDataContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public List<Institution> findAll() {
        return RemoteReferenceDataHttp.getList(ctx, PATH, InstitutionDto.class).stream()
                .map(InstitutionDto::toDomain)
                .toList();
    }

    @Override
    public Optional<Institution> findByInstitutionCode(String hubInstitutionCode) {
        return findAll().stream()
                .filter(i -> i.getInstitutionCode().equals(hubInstitutionCode))
                .findFirst();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class InstitutionDto {
        @JsonProperty("institutionCode")
        String institutionCode;
        @JsonProperty("displayName")
        String displayName;
        @JsonProperty("active")
        boolean active;

        Institution toDomain() {
            return new Institution(institutionCode, displayName, active);
        }
    }
}
