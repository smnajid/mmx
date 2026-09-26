CREATE TABLE institution (
    institution_code VARCHAR(32)  NOT NULL PRIMARY KEY,
    display_name     VARCHAR(128) NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE money_market_order
    ADD COLUMN institution_code VARCHAR(32) NULL;
