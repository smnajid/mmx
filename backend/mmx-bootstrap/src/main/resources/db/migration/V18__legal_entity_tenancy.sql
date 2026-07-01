-- Organisation and LegalEntity reference data (ADR-0001: one deployment per Organisation)
CREATE TABLE organisation (
    code VARCHAR(4) NOT NULL,
    CONSTRAINT pk_organisation PRIMARY KEY (code)
);

CREATE TABLE legal_entity (
    code                VARCHAR(3)  NOT NULL,
    organisation_code   VARCHAR(4)  NOT NULL,
    role                VARCHAR(20) NOT NULL,
    connected_hub_code  VARCHAR(3)  NULL,
    CONSTRAINT pk_legal_entity PRIMARY KEY (code),
    CONSTRAINT fk_legal_entity_organisation FOREIGN KEY (organisation_code) REFERENCES organisation (code),
    CONSTRAINT fk_legal_entity_connected_hub FOREIGN KEY (connected_hub_code) REFERENCES legal_entity (code),
    CONSTRAINT chk_legal_entity_role CHECK (role IN ('TRADING_HUB', 'TRADING_CLIENT'))
);

CREATE TABLE mmx_user (
    id VARCHAR(100) NOT NULL,
    CONSTRAINT pk_mmx_user PRIMARY KEY (id)
);

CREATE TABLE mmx_user_scope (
    user_id             VARCHAR(100) NOT NULL,
    legal_entity_code   VARCHAR(3)  NOT NULL,
    role                VARCHAR(30) NOT NULL,
    CONSTRAINT pk_mmx_user_scope PRIMARY KEY (user_id, legal_entity_code, role),
    CONSTRAINT fk_mmx_user_scope_user FOREIGN KEY (user_id) REFERENCES mmx_user (id),
    CONSTRAINT fk_mmx_user_scope_entity FOREIGN KEY (legal_entity_code) REFERENCES legal_entity (code),
    CONSTRAINT chk_mmx_user_scope_role CHECK (role IN ('TRADER', 'CLIENT_REPRESENTATIVE'))
);

-- Default organisation and entities for existing single-hub deployments
INSERT INTO organisation (code) VALUES ('LODH');

INSERT INTO legal_entity (code, organisation_code, role, connected_hub_code)
VALUES ('LOC', 'LODH', 'TRADING_HUB', NULL);

INSERT INTO legal_entity (code, organisation_code, role, connected_hub_code)
VALUES ('PAR', 'LODH', 'TRADING_CLIENT', 'LOC');

-- Partition orders by LegalEntity; back-fill existing rows to default hub
ALTER TABLE money_market_order ADD COLUMN legal_entity_code VARCHAR(3);

UPDATE money_market_order SET legal_entity_code = 'LOC' WHERE legal_entity_code IS NULL;

ALTER TABLE money_market_order ALTER COLUMN legal_entity_code SET NOT NULL;

ALTER TABLE money_market_order
    ADD CONSTRAINT fk_order_legal_entity FOREIGN KEY (legal_entity_code) REFERENCES legal_entity (code);

DROP INDEX IF EXISTS idx_order_ext_ref;

CREATE UNIQUE INDEX idx_order_ext_ref_entity
    ON money_market_order (legal_entity_code, external_order_reference);

CREATE INDEX idx_order_legal_entity_status_type
    ON money_market_order (legal_entity_code, status, order_type);
