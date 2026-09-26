package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidInstitutionException;

import java.util.Objects;
import java.util.Optional;

/**
 * An institution owned by one LegalEntity: a TradingHub's native institution, or a TradingClient's
 * onboarded institution permanently linked to a hub-native institution. Carries the owner's counterparty
 * accounts and whether it is closed to new business ({@code !active}: a deactivated native institution or
 * an offboarded onboarded institution). {@code version} increments on every change to the exported state,
 * so each behaviour method reports whether that state changed.
 */
public final class Institution {

    private final String institutionCode;
    private final String displayName;
    private final LegalEntityCode owningLegalEntityCode;
    private final HubInstitutionLink hubLink;
    private CounterpartyAccounts counterpartyAccounts;
    private boolean active;
    private long version;

    public Institution(
            String institutionCode,
            String displayName,
            LegalEntityCode owningLegalEntityCode,
            HubInstitutionLink hubLink,
            CounterpartyAccounts counterpartyAccounts,
            boolean active,
            long version) {
        this.institutionCode = validateCode(institutionCode);
        this.displayName = validateDisplayName(displayName);
        this.owningLegalEntityCode = owningLegalEntityCode;
        this.hubLink = hubLink;
        this.counterpartyAccounts = Objects.requireNonNull(counterpartyAccounts, "counterpartyAccounts");
        this.active = active;
        this.version = version;
    }

    /** Native institution without owner or accounts (catalog reads, e.g. a hub catalog seen from a client). */
    public Institution(String institutionCode, String displayName, boolean active) {
        this(institutionCode, displayName, null, null, CounterpartyAccounts.none(), active, 0);
    }

    public static Institution createNative(
            String institutionCode,
            String displayName,
            LegalEntityCode owningLegalEntityCode,
            CounterpartyAccounts counterpartyAccounts) {
        Objects.requireNonNull(owningLegalEntityCode, "owningLegalEntityCode");
        return new Institution(institutionCode, displayName, owningLegalEntityCode, null, counterpartyAccounts, true, 1);
    }

    public static Institution onboardFromGrant(
            String institutionCode,
            String hubInstitutionDisplayName,
            HubInstitutionLink hubLink,
            LegalEntityCode owningLegalEntityCode,
            CounterpartyAccounts counterpartyAccounts) {
        Objects.requireNonNull(hubLink, "hubLink");
        Objects.requireNonNull(owningLegalEntityCode, "owningLegalEntityCode");
        return new Institution(
                institutionCode,
                deriveDisplayName(hubInstitutionDisplayName, hubLink.hubLegalEntityCode()),
                owningLegalEntityCode,
                hubLink,
                counterpartyAccounts,
                true,
                1);
    }

    public static String deriveDisplayName(String hubInstitutionDisplayName, LegalEntityCode hubLegalEntityCode) {
        if (hubInstitutionDisplayName == null || hubInstitutionDisplayName.isBlank()) {
            throw new InvalidInstitutionException("hub institution displayName is required to derive the onboarded name");
        }
        return hubInstitutionDisplayName.trim() + " via " + hubLegalEntityCode.value();
    }

    /** Client institution offboarding. */
    public boolean offboard() {
        return setActive(false);
    }

    /** Client re-onboarding of an offboarded institution. */
    public boolean reopen() {
        return setActive(true);
    }

    /** Hub-institution deactivation. */
    public boolean deactivate() {
        return setActive(false);
    }

    /** Hub-institution reactivation. */
    public boolean reactivate() {
        return setActive(true);
    }

    public boolean changeAccounts(CounterpartyAccounts accounts) {
        Objects.requireNonNull(accounts, "accounts");
        if (counterpartyAccounts.equals(accounts)) {
            return false;
        }
        counterpartyAccounts = accounts;
        version++;
        return true;
    }

    private boolean setActive(boolean newActive) {
        if (active == newActive) {
            return false;
        }
        active = newActive;
        version++;
        return true;
    }

    public String getInstitutionCode() {
        return institutionCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LegalEntityCode getOwningLegalEntityCode() {
        return owningLegalEntityCode;
    }

    public Optional<HubInstitutionLink> getHubLink() {
        return Optional.ofNullable(hubLink);
    }

    public boolean isOnboarded() {
        return hubLink != null;
    }

    public CounterpartyAccounts getCounterpartyAccounts() {
        return counterpartyAccounts;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isClosedToNewBusiness() {
        return !active;
    }

    public long getVersion() {
        return version;
    }

    public static String validateDisplayName(String displayName) {
        if (displayName == null) {
            throw new InvalidInstitutionException("displayName is required");
        }
        String trimmed = displayName.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidInstitutionException("displayName must not be blank");
        }
        if (trimmed.length() > 128) {
            throw new InvalidInstitutionException("displayName must not exceed 128 characters");
        }
        return trimmed;
    }

    public static String validateCode(String institutionCode) {
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new InvalidInstitutionException("institutionCode is required");
        }
        if (institutionCode.length() > 32) {
            throw new InvalidInstitutionException("institutionCode must not exceed 32 characters");
        }
        return institutionCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Institution that)) {
            return false;
        }
        return active == that.active
                && version == that.version
                && Objects.equals(institutionCode, that.institutionCode)
                && Objects.equals(displayName, that.displayName)
                && Objects.equals(owningLegalEntityCode, that.owningLegalEntityCode)
                && Objects.equals(hubLink, that.hubLink)
                && Objects.equals(counterpartyAccounts, that.counterpartyAccounts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(institutionCode, displayName, owningLegalEntityCode, hubLink, active, version);
    }
}
