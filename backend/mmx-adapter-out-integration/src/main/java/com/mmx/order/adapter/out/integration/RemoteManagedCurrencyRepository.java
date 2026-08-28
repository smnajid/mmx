package com.mmx.order.adapter.out.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Remote-backed {@link ManagedCurrencyRepository}: reads the hub's managed currencies live from LODH
 * via REST. CGED stores zero hub reference data locally.
 */
public final class RemoteManagedCurrencyRepository implements ManagedCurrencyRepository {

    private final RemoteReferenceDataContext ctx;

    public RemoteManagedCurrencyRepository(RemoteReferenceDataContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public List<ManagedCurrency> findAll() {
        return RemoteReferenceDataHttp.getList(ctx, "/api/v1/cross-org/reference/currencies", CurrencyDto.class)
                .stream()
                .map(CurrencyDto::toDomain)
                .toList();
    }

    @Override
    public List<ManagedCurrency> findAllByLegalEntityCode(LegalEntityCode legalEntityCode) {
        return findAll();
    }

    @Override
    public Optional<ManagedCurrency> findByCode(String code) {
        return findAll().stream().filter(c -> c.getCode().equals(code)).findFirst();
    }

    @Override
    public boolean existsByCode(String code) {
        return findByCode(code).isPresent();
    }

    @Override
    public ManagedCurrency save(ManagedCurrency currency) {
        throw new UnsupportedOperationException(
                "Remote-backed ManagedCurrencyRepository is read-only; CGED does not master hub reference data");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class CurrencyDto {
        @JsonProperty("code")
        String code;
        @JsonProperty("active")
        boolean active;
        @JsonProperty("minSubscriptionAmount")
        double minSubscriptionAmount;
        @JsonProperty("minIncreaseDecreaseAmount")
        double minIncreaseDecreaseAmount;
        @JsonProperty("enabledTenors")
        List<String> enabledTenors;
        @JsonProperty("enabledNoticePeriods")
        List<String> enabledNoticePeriods;

        ManagedCurrency toDomain() {
            Set<Tenor> tenors = enabledTenors != null
                    ? enabledTenors.stream()
                            .map(Tenor::fromCode)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toSet())
                    : Set.of();
            Set<NoticePeriod> notices = enabledNoticePeriods != null
                    ? enabledNoticePeriods.stream()
                            .map(NoticePeriod::fromCode)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toSet())
                    : Set.of();
            return new ManagedCurrency(
                    code,
                    active,
                    BigDecimal.valueOf(minSubscriptionAmount),
                    BigDecimal.valueOf(minIncreaseDecreaseAmount),
                    tenors,
                    notices);
        }
    }
}
