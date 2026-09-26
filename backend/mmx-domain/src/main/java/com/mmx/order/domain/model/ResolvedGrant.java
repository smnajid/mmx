package com.mmx.order.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable snapshot of a {@link DelegatedInstitutionGrant} at the moment it was resolved (e.g. at
 * intake). Capturing the snapshot at routing time is what makes grants prospective: later edits to
 * the live grant (update/deactivate) do not mutate a previously captured snapshot, so already-routed
 * or executed orders keep the grant in effect when they were routed.
 */
public record ResolvedGrant(
        DelegatedGrantKey key,
        Set<Tenor> enabledTenors,
        Set<NoticePeriod> enabledNoticePeriods,
        boolean active) {

    public ResolvedGrant {
        enabledTenors = enabledTenors.isEmpty() ? EnumSet.noneOf(Tenor.class) : EnumSet.copyOf(enabledTenors);
        enabledNoticePeriods =
                enabledNoticePeriods.isEmpty()
                        ? EnumSet.noneOf(NoticePeriod.class)
                        : EnumSet.copyOf(enabledNoticePeriods);
    }

    public static ResolvedGrant from(DelegatedInstitutionGrant grant) {
        return new ResolvedGrant(
                grant.key(), grant.getEnabledTenors(), grant.getEnabledNoticePeriods(), grant.isActive());
    }

    public boolean permits(Tenor tenor) {
        return active && enabledTenors.contains(tenor);
    }

    public boolean permits(NoticePeriod notice) {
        return active && enabledNoticePeriods.contains(notice);
    }
}
