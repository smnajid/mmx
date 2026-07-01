package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.InvalidDelegatedGrantException;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates that a delegated grant's enabled tenors and notice periods are subsets of the hub's
 * managed-currency enabled sets, and parses configurable code strings into enum sets.
 *
 * <p>Allowed tenor codes: {@code 1W, 2W, 1M, 3M, 6M, 1Y}. Allowed notice codes: {@code 24H, 48H}.
 */
public final class DelegatedGrantSubsetPolicy {

    private DelegatedGrantSubsetPolicy() {}

    public static void validate(
            Set<Tenor> grantTenors,
            Set<NoticePeriod> grantNotices,
            ManagedCurrency hubCurrency) {
        Set<Tenor> hubTenors = hubCurrency.getEnabledTenors();
        for (Tenor tenor : grantTenors) {
            if (!hubTenors.contains(tenor)) {
                throw new InvalidDelegatedGrantException(
                        "enabledTenors must be a subset of the hub managed-currency enabledTenors: "
                                + tenor.getCode() + " is not enabled on the hub");
            }
        }
        Set<NoticePeriod> hubNotices = hubCurrency.getEnabledNoticePeriods();
        for (NoticePeriod notice : grantNotices) {
            if (!hubNotices.contains(notice)) {
                throw new InvalidDelegatedGrantException(
                        "enabledNoticePeriods must be a subset of the hub managed-currency enabledNoticePeriods: "
                                + notice.getCode() + " is not enabled on the hub");
            }
        }
    }

    public static Set<Tenor> parseTenorCodes(List<String> codes) {
        if (codes == null) {
            return EnumSet.noneOf(Tenor.class);
        }
        Set<Tenor> tenors = new LinkedHashSet<>();
        for (String code : codes) {
            tenors.add(parseTenor(code));
        }
        return tenors.isEmpty() ? EnumSet.noneOf(Tenor.class) : EnumSet.copyOf(tenors);
    }

    public static Set<NoticePeriod> parseNoticeCodes(List<String> codes) {
        if (codes == null) {
            return EnumSet.noneOf(NoticePeriod.class);
        }
        Set<NoticePeriod> notices = new LinkedHashSet<>();
        for (String code : codes) {
            notices.add(parseNotice(code));
        }
        return notices.isEmpty() ? EnumSet.noneOf(NoticePeriod.class) : EnumSet.copyOf(notices);
    }

    private static Tenor parseTenor(String code) {
        return Tenor.fromCode(code)
                .orElseThrow(
                        () ->
                                new InvalidDelegatedGrantException(
                                        "Invalid tenor code: " + code
                                                + ". Allowed: 1W, 2W, 1M, 3M, 6M, 1Y"));
    }

    private static NoticePeriod parseNotice(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidDelegatedGrantException("Invalid notice period code: blank. Allowed: 24H, 48H");
        }
        String normalized = code.trim();
        for (NoticePeriod notice : NoticePeriod.values()) {
            if (notice.getCode().equals(normalized)) {
                return notice;
            }
        }
        throw new InvalidDelegatedGrantException(
                "Invalid notice period code: " + code + ". Allowed: 24H, 48H");
    }
}
