-- Delegated institution grants (hub → client per currency) and proxy institution columns.

CREATE TABLE delegated_institution_grant (
    hub_institution_code      VARCHAR(32) NOT NULL,
    client_legal_entity_code  VARCHAR(3)  NOT NULL,
    currency                  VARCHAR(3)  NOT NULL,
    active                    BOOLEAN     NOT NULL DEFAULT TRUE,
    tenor_1w                  BOOLEAN     NOT NULL DEFAULT FALSE,
    tenor_2w                  BOOLEAN     NOT NULL DEFAULT FALSE,
    tenor_1m                  BOOLEAN     NOT NULL DEFAULT FALSE,
    tenor_3m                  BOOLEAN     NOT NULL DEFAULT FALSE,
    tenor_6m                  BOOLEAN     NOT NULL DEFAULT FALSE,
    tenor_1y                  BOOLEAN     NOT NULL DEFAULT FALSE,
    notice_24h                BOOLEAN     NOT NULL DEFAULT FALSE,
    notice_48h                BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_delegated_institution_grant
        PRIMARY KEY (hub_institution_code, client_legal_entity_code, currency),
    CONSTRAINT fk_grant_hub_institution
        FOREIGN KEY (hub_institution_code) REFERENCES institution (institution_code),
    CONSTRAINT fk_grant_client_entity
        FOREIGN KEY (client_legal_entity_code) REFERENCES legal_entity (code)
);

CREATE INDEX idx_delegated_grant_client
    ON delegated_institution_grant (client_legal_entity_code);

ALTER TABLE institution ADD COLUMN legal_entity_code VARCHAR(3) NULL;
ALTER TABLE institution ADD COLUMN hub_legal_entity_code VARCHAR(3) NULL;
ALTER TABLE institution ADD COLUMN hub_institution_code VARCHAR(32) NULL;

UPDATE institution SET legal_entity_code = 'LOC' WHERE legal_entity_code IS NULL;

ALTER TABLE institution ALTER COLUMN legal_entity_code SET NOT NULL;

ALTER TABLE institution
    ADD CONSTRAINT fk_institution_legal_entity
        FOREIGN KEY (legal_entity_code) REFERENCES legal_entity (code);

ALTER TABLE institution
    ADD CONSTRAINT fk_institution_hub_entity
        FOREIGN KEY (hub_legal_entity_code) REFERENCES legal_entity (code);

ALTER TABLE institution
    ADD CONSTRAINT fk_institution_hub_institution
        FOREIGN KEY (hub_institution_code) REFERENCES institution (institution_code);

CREATE INDEX idx_institution_client_proxy ON institution (legal_entity_code, hub_institution_code);

ALTER TABLE managed_currency ADD COLUMN legal_entity_code VARCHAR(3) NULL;
UPDATE managed_currency SET legal_entity_code = 'LOC' WHERE legal_entity_code IS NULL;
ALTER TABLE managed_currency ALTER COLUMN legal_entity_code SET NOT NULL;
ALTER TABLE managed_currency
    ADD CONSTRAINT fk_managed_currency_legal_entity
        FOREIGN KEY (legal_entity_code) REFERENCES legal_entity (code);

ALTER TABLE term_rate ADD COLUMN legal_entity_code VARCHAR(3) NULL;
UPDATE term_rate SET legal_entity_code = 'LOC' WHERE legal_entity_code IS NULL;
ALTER TABLE term_rate ALTER COLUMN legal_entity_code SET NOT NULL;
ALTER TABLE term_rate
    ADD CONSTRAINT fk_term_rate_legal_entity
        FOREIGN KEY (legal_entity_code) REFERENCES legal_entity (code);

ALTER TABLE oncall_rate_segment ADD COLUMN legal_entity_code VARCHAR(3) NULL;
UPDATE oncall_rate_segment SET legal_entity_code = 'LOC' WHERE legal_entity_code IS NULL;
ALTER TABLE oncall_rate_segment ALTER COLUMN legal_entity_code SET NOT NULL;
ALTER TABLE oncall_rate_segment
    ADD CONSTRAINT fk_oncall_rate_segment_legal_entity
        FOREIGN KEY (legal_entity_code) REFERENCES legal_entity (code);
