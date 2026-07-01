package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidDelegatedGrantException;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A TradingHub's grant of one of its native institutions to a TradingClient for a currency, with an
 * enabled subset of Term tenors and OnCall notice periods. Independent of the hub's own intake
 * enablement; prospective-only. Keyed by {@link DelegatedGrantKey}.
 */
public final class DelegatedInstitutionGrant {

    private final DelegatedGrantKey key;
    private final Set<Tenor> enabledTenors;
    private final Set<NoticePeriod> enabledNoticePeriods;
    private final boolean active;

    public DelegatedInstitutionGrant(
            String hubInstitutionCode,
            LegalEntityCode clientLegalEntityCode,
            String currency,
            Set<Tenor> enabledTenors,
            Set<NoticePeriod> enabledNoticePeriods,
            boolean active) {
        this.key = new DelegatedGrantKey(hubInstitutionCode, clientLegalEntityCode, currency);
        this.enabledTenors = copyTenors(enabledTenors);
        this.enabledNoticePeriods = copyNotices(enabledNoticePeriods);
        this.active = active;
    }

    private static Set<Tenor> copyTenors(Set<Tenor> tenors) {
        if (tenors == null) {
            throw new InvalidDelegatedGrantException("enabledTenors must not be null");
        }
        return tenors.isEmpty() ? EnumSet.noneOf(Tenor.class) : EnumSet.copyOf(tenors);
    }

    private static Set<NoticePeriod> copyNotices(Set<NoticePeriod> notices) {
        if (notices == null) {
            throw new InvalidDelegatedGrantException("enabledNoticePeriods must not be null");
        }
        return notices.isEmpty() ? EnumSet.noneOf(NoticePeriod.class) : EnumSet.copyOf(notices);
    }

    public DelegatedGrantKey key() {
        return key;
    }

    public String getHubInstitutionCode() {
        return key.hubInstitutionCode();
    }

    public LegalEntityCode getClientLegalEntityCode() {
        return key.clientLegalEntityCode();
    }

    public String getCurrency() {
        return key.currency();
    }

    public Set<Tenor> getEnabledTenors() {
        return enabledTenors.isEmpty() ? EnumSet.noneOf(Tenor.class) : EnumSet.copyOf(enabledTenors);
    }

    public Set<NoticePeriod> getEnabledNoticePeriods() {
        return enabledNoticePeriods.isEmpty()
                ? EnumSet.noneOf(NoticePeriod.class)
                : EnumSet.copyOf(enabledNoticePeriods);
    }

    public boolean isActive() {
        return active;
    }

    public DelegatedInstitutionGrant withActive(boolean active) {
        return new DelegatedInstitutionGrant(
                key.hubInstitutionCode(),
                key.clientLegalEntityCode(),
                key.currency(),
                enabledTenors,
                enabledNoticePeriods,
                active);
    }

    public DelegatedInstitutionGrant withEnabledSets(
            Set<Tenor> enabledTenors, Set<NoticePeriod> enabledNoticePeriods) {
        return new DelegatedInstitutionGrant(
                key.hubInstitutionCode(),
                key.clientLegalEntityCode(),
                key.currency(),
                enabledTenors,
                enabledNoticePeriods,
                active);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DelegatedInstitutionGrant that)) {
            return false;
        }
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
