-- Client institution onboarding, counterparty accounts, and institution export (ADR 0008).
-- Thin proxies become onboarded institutions in place: same rows, codes, and hub links; accounts start null.

ALTER TABLE institution ADD COLUMN term_counterparty_account VARCHAR(34) NULL;
ALTER TABLE institution ADD COLUMN oncall_counterparty_account VARCHAR(34) NULL;
ALTER TABLE institution ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- A remote client stores neither the hub's institution row nor the hub's LegalEntity (ADR 0007), so the
-- hub link becomes plain values, validated at onboarding against the granted list.
ALTER TABLE institution DROP CONSTRAINT fk_institution_hub_institution;
ALTER TABLE institution DROP CONSTRAINT fk_institution_hub_entity;

-- At most one onboarded institution per hub institution per client. Native rows have NULL hub columns and,
-- NULLs being distinct in a unique index, never collide.
CREATE UNIQUE INDEX uq_institution_onboarded_hub
    ON institution (legal_entity_code, hub_legal_entity_code, hub_institution_code);

-- Counterparty account snapshots: stamped at execute, and (hub-side routed orders) the client's account
-- stamped at routing.
ALTER TABLE money_market_order ADD COLUMN counterparty_account VARCHAR(34) NULL;
ALTER TABLE money_market_order ADD COLUMN client_counterparty_account VARCHAR(34) NULL;

-- Client enablement per (onboarded institution, currency); no row means nothing is enabled.
CREATE TABLE client_institution_enablement (
    institution_code   VARCHAR(32) NOT NULL,
    currency           VARCHAR(3)  NOT NULL,
    legal_entity_code  VARCHAR(3)  NOT NULL,
    tenor_1w           BOOLEAN     NOT NULL DEFAULT FALSE,
    tenor_2w           BOOLEAN     NOT NULL DEFAULT FALSE,
    tenor_1m           BOOLEAN     NOT NULL DEFAULT FALSE,
    tenor_3m           BOOLEAN     NOT NULL DEFAULT FALSE,
    tenor_6m           BOOLEAN     NOT NULL DEFAULT FALSE,
    tenor_1y           BOOLEAN     NOT NULL DEFAULT FALSE,
    notice_24h         BOOLEAN     NOT NULL DEFAULT FALSE,
    notice_48h         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_client_institution_enablement PRIMARY KEY (institution_code, currency),
    CONSTRAINT fk_client_enablement_institution
        FOREIGN KEY (institution_code) REFERENCES institution (institution_code),
    CONSTRAINT fk_client_enablement_legal_entity
        FOREIGN KEY (legal_entity_code) REFERENCES legal_entity (code)
);

-- Institution export outbox: one row per exported change, committed with the institution change and
-- drained to mmx.institution.{legal_entity_code}.
CREATE TABLE institution_export_outbox (
    id                 UUID        NOT NULL,
    legal_entity_code  VARCHAR(3)  NOT NULL,
    institution_code   VARCHAR(32) NOT NULL,
    version            BIGINT      NOT NULL,
    payload            TEXT        NOT NULL,
    status             VARCHAR(20) NOT NULL,
    publish_attempts   INT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    last_attempt_at    TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT pk_institution_export_outbox PRIMARY KEY (id),
    CONSTRAINT uq_institution_export_version UNIQUE (legal_entity_code, institution_code, version)
);

CREATE INDEX idx_institution_export_outbox_status_created ON institution_export_outbox (status, created_at);
