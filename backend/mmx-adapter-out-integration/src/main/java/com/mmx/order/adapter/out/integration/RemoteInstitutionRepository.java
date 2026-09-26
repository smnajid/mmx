package com.mmx.order.adapter.out.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.List;
import java.util.Optional;

/**
 * Remote-backed {@link InstitutionRepository}: reads the hub's onboarded institutions live from LODH
 * via REST. Read-only; CGED does not master hub institution data.
 */
public final class RemoteInstitutionRepository implements InstitutionRepository {

    private final RemoteReferenceDataContext ctx;

    public RemoteInstitutionRepository(RemoteReferenceDataContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public List<Institution> findAll() {
        return RemoteReferenceDataHttp.getList(
                        ctx, "/api/v1/cross-org/reference/institutions", InstitutionDto.class)
                .stream()
                .map(InstitutionDto::toDomain)
                .toList();
    }

    @Override
    public List<Institution> findNativeByLegalEntityCode(LegalEntityCode legalEntityCode) {
        return findAll();
    }

    @Override
    public List<Institution> findActive() {
        return RemoteReferenceDataHttp.getList(
                        ctx,
                        "/api/v1/cross-org/reference/institutions?activeOnly=true",
                        InstitutionDto.class)
                .stream()
                .map(InstitutionDto::toDomain)
                .toList();
    }

    @Override
    public Optional<Institution> findByInstitutionCode(String institutionCode) {
        return findAll().stream()
                .filter(i -> i.getInstitutionCode().equals(institutionCode))
                .findFirst();
    }

    @Override
    public boolean existsAny() {
        return !findAll().isEmpty();
    }

    @Override
    public int maxSuffixForAcronym(String acronymBase) {
        return 0;
    }

    @Override
    public Institution save(Institution institution) {
        throw new UnsupportedOperationException(
                "Remote-backed InstitutionRepository is read-only; CGED does not master hub reference data");
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
