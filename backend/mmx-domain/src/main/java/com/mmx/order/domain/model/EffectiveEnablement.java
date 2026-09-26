package com.mmx.order.domain.model;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * What a TradingClient order may use for new business: the active grant's enabled set intersected with the
 * client enablement. Computed on every read and never stored, so a grant reduction caps the client's choice
 * without erasing it and a grant expansion never switches anything on.
 */
public record EffectiveEnablement(
        Set<Tenor> tenors,
        Set<NoticePeriod> noticePeriods,
        Set<Tenor> enabledNotGrantedTenors,
        Set<NoticePeriod> enabledNotGrantedNoticePeriods) {

    public static EffectiveEnablement of(Optional<DelegatedInstitutionGrant> grant, ClientEnablement client) {
        Optional<DelegatedInstitutionGrant> active = grant.filter(DelegatedInstitutionGrant::isActive);
        Set<Tenor> grantedTenors =
                active.map(DelegatedInstitutionGrant::getEnabledTenors).orElse(EnumSet.noneOf(Tenor.class));
        Set<NoticePeriod> grantedNotices =
                active.map(DelegatedInstitutionGrant::getEnabledNoticePeriods).orElse(EnumSet.noneOf(NoticePeriod.class));

        Set<Tenor> tenors = copy(client.tenors(), Tenor.class);
        tenors.retainAll(grantedTenors);
        Set<NoticePeriod> notices = copy(client.noticePeriods(), NoticePeriod.class);
        notices.retainAll(grantedNotices);
        Set<Tenor> notGrantedTenors = copy(client.tenors(), Tenor.class);
        notGrantedTenors.removeAll(grantedTenors);
        Set<NoticePeriod> notGrantedNotices = copy(client.noticePeriods(), NoticePeriod.class);
        notGrantedNotices.removeAll(grantedNotices);
        return new EffectiveEnablement(tenors, notices, notGrantedTenors, notGrantedNotices);
    }

    public boolean permits(Tenor tenor) {
        return tenors.contains(tenor);
    }

    public boolean permits(NoticePeriod noticePeriod) {
        return noticePeriods.contains(noticePeriod);
    }

    private static <E extends Enum<E>> Set<E> copy(Set<E> values, Class<E> type) {
        return values.isEmpty() ? EnumSet.noneOf(type) : EnumSet.copyOf(values);
    }
}
