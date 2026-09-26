-- Demo MMXUser identities for local development (matches frontend DEFAULT_USER_ID and seed scripts).

INSERT INTO mmx_user (id) VALUES ('demo-trader');
INSERT INTO mmx_user (id) VALUES ('demo-trader-2');

INSERT INTO mmx_user_scope (user_id, legal_entity_code, role)
VALUES ('demo-trader', 'LOC', 'TRADER');

INSERT INTO mmx_user_scope (user_id, legal_entity_code, role)
VALUES ('demo-trader', 'PAR', 'CLIENT_REPRESENTATIVE');

INSERT INTO mmx_user_scope (user_id, legal_entity_code, role)
VALUES ('demo-trader-2', 'LOC', 'TRADER');
