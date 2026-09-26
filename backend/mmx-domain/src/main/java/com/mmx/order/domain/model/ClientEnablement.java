package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidInstitutionException;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The tenors (Term) and notice periods (OnCall) a ClientRepresentative switched on for new business on an
 * onboarded institution, per currency. Opt-in (empty at onboarding), client-owned, and never pruned when
 * the grant shrinks: {@link EffectiveEnablement} caps it at read time.
 */
public record ClientEnablement(
        String institutionCode, String currency, Set<Tenor> tenors, Set<NoticePeriod> noticePeriods) {

    public ClientEnablement {
        institutionCode = Institution.validateCode(institutionCode);
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new InvalidInstitutionException("currency must be a three-letter ISO 4217 code");
        }
        tenors = tenors == null || tenors.isEmpty() ? EnumSet.noneOf(Tenor.class) : EnumSet.copyOf(tenors);
        noticePeriods =
                noticePeriods == null || noticePeriods.isEmpty()
                        ? EnumSet.noneOf(NoticePeriod.class)
                        : EnumSet.copyOf(noticePeriods);
    }

    public static ClientEnablement empty(String institutionCode, String currency) {
        return new ClientEnablement(institutionCode, currency, Set.of(), Set.of());
    }

    public boolean isEmpty() {
        return tenors.isEmpty() && noticePeriods.isEmpty();
    }

    /**
     * Full replacement of both sets. Every value switched on by this replacement must be enabled by the
     * current active grant; values already enabled may be kept even if the grant no longer enables them.
     */
    public ClientEnablement replaceWith(
            Set<Tenor> newTenors, Set<NoticePeriod> newNoticePeriods, Optional<DelegatedInstitutionGrant> grant) {
        ClientEnablement replacement = new ClientEnablement(institutionCode, currency, newTenors, newNoticePeriods);
        Optional<DelegatedInstitutionGrant> active = grant.filter(DelegatedInstitutionGrant::isActive);
        Set<Tenor> grantedTenors =
                active.map(DelegatedInstitutionGrant::getEnabledTenors).orElse(EnumSet.noneOf(Tenor.class));
        Set<NoticePeriod> grantedNotices =
                active.map(DelegatedInstitutionGrant::getEnabledNoticePeriods).orElse(EnumSet.noneOf(NoticePeriod.class));
        for (Tenor tenor : replacement.tenors) {
            if (!tenors.contains(tenor) && !grantedTenors.contains(tenor)) {
                throw new InvalidInstitutionException(
                        "Tenor " + tenor.getCode() + " is not granted for " + currency + "; it cannot be enabled");
            }
        }
        for (NoticePeriod notice : replacement.noticePeriods) {
            if (!noticePeriods.contains(notice) && !grantedNotices.contains(notice)) {
                throw new InvalidInstitutionException(
                        "Notice period " + notice.getCode() + " is not granted for " + currency
                                + "; it cannot be enabled");
            }
        }
        return replacement;
    }
}
