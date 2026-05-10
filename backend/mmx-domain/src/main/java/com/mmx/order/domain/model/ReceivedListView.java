package com.mmx.order.domain.model;

/** Horizon for workspace Received queues (US3 / FR-004). */
public enum ReceivedListView {

    /** Today through today+2 calendar days in the business timezone (Europe/Paris). */
    NEAR_TERM,

    /** Full RECEIVED list for the workspace order type (no valueDate window). */
    ALL
}
