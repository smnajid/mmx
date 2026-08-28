package com.mmx.order.adapter.out.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Remote-backed {@link DelegatedGrantRepository}: reads delegated institution grants live from LODH
 * via REST. LODH auto-scopes the returned grants to the proven {@code X-MMX-CrossOrg-Key} client.
 * Read-only; CGED does not master delegated grants.
 */
public final class RemoteDelegatedGrantRepository implements DelegatedGrantRepository {

    private final RemoteReferenceDataContext ctx;

    public RemoteDelegatedGrantRepository(RemoteReferenceDataContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public List<DelegatedInstitutionGrant> findAll() {
        return RemoteReferenceDataHttp.getList(
                        ctx, "/api/v1/cross-org/reference/grants", GrantDto.class)
                .stream()
                .map(GrantDto::toDomain)
                .toList();
    }

    @Override
    public List<DelegatedInstitutionGrant> findByClientLegalEntityCode(LegalEntityCode clientLegalEntityCode) {
        return findAll().stream()
                .filter(g -> g.getClientLegalEntityCode().equals(clientLegalEntityCode))
                .toList();
    }

    @Override
    public Optional<DelegatedInstitutionGrant> findByKey(DelegatedGrantKey key) {
        return findAll().stream().filter(g -> g.key().equals(key)).findFirst();
    }

    @Override
    public boolean existsByKey(DelegatedGrantKey key) {
        return findByKey(key).isPresent();
    }

    @Override
    public boolean existsActiveGrantForHubInstitutionAndClient(
            String hubInstitutionCode, LegalEntityCode clientLegalEntityCode) {
        return findAll().stream()
                .anyMatch(
                        g ->
                                g.isActive()
                                        && g.getHubInstitutionCode().equals(hubInstitutionCode)
                                        && g.getClientLegalEntityCode().equals(clientLegalEntityCode));
    }

    @Override
    public DelegatedInstitutionGrant save(DelegatedInstitutionGrant grant) {
        throw new UnsupportedOperationException(
                "Remote-backed DelegatedGrantRepository is read-only; CGED does not master hub grants");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class GrantDto {
        @JsonProperty("hubInstitutionCode")
        String hubInstitutionCode;
        @JsonProperty("clientLegalEntityCode")
        String clientLegalEntityCode;
        @JsonProperty("currency")
        String currency;
        @JsonProperty("enabledTenors")
        List<String> enabledTenors;
        @JsonProperty("enabledNoticePeriods")
        List<String> enabledNoticePeriods;
        @JsonProperty("active")
        boolean active;

        DelegatedInstitutionGrant toDomain() {
            Set<Tenor> tenors =
                    enabledTenors != null
                            ? enabledTenors.stream()
                                    .map(Tenor::fromCode)
                                    .filter(Optional::isPresent)
                                    .map(Optional::get)
                                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Tenor.class)))
                            : EnumSet.noneOf(Tenor.class);
            Set<NoticePeriod> notices =
                    enabledNoticePeriods != null
                            ? enabledNoticePeriods.stream()
                                    .map(NoticePeriod::fromCode)
                                    .filter(Optional::isPresent)
                                    .map(Optional::get)
                                    .collect(
                                            Collectors.toCollection(() -> EnumSet.noneOf(NoticePeriod.class)))
                            : EnumSet.noneOf(NoticePeriod.class);
            return new DelegatedInstitutionGrant(
                    hubInstitutionCode,
                    new LegalEntityCode(clientLegalEntityCode),
                    currency,
                    tenors,
                    notices,
                    active);
        }
    }
}
