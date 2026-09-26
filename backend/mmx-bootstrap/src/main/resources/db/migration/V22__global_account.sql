CREATE TABLE global_account (
    client_legal_entity_code VARCHAR(3) NOT NULL,
    hub_legal_entity_code VARCHAR(3) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    account_ref VARCHAR(50) NOT NULL,
    PRIMARY KEY (client_legal_entity_code, hub_legal_entity_code, currency)
);
