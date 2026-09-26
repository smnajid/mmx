package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.Institution;

/**
 * Records an institution export ({@code InstitutionUpdatedV1}) for the institution's owning LegalEntity, in
 * the same transaction as the change. The payload is frozen at record time.
 */
public interface InstitutionExportOutbox {

    void schedule(Institution institution, ChangeReason reason);

    enum ChangeReason {
        ONBOARDED,
        REONBOARDED,
        ACCOUNTS_CHANGED,
        OFFBOARDED,
        DEACTIVATED,
        REACTIVATED
    }
}
