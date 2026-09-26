-- Cross-org demo topology registration (local development): CGD@CGEG → LOC@LODH.
--
-- Wires both halves of the cross-org connection (see openspec/specs/legal-entity-tenancy/spec.md
-- "bidirectional connection" and docs/adr/0007-cross-org-trust-boundary.md):
--   * the CGEG deployment needs its own LegalEntity CGD (connected to hub LOC) plus a LOC row so the
--     connectedHubCode FK resolves;
--   * the LODH deployment needs CGD registered in its legal_entity table — membership in LOC's
--     TradingClient list is what JpaCrossOrgMembershipPort checks at leg-A accept.
--
-- All inserts are additive with ON CONFLICT DO NOTHING so the same migration is safe on every
-- deployment (V18 already seeded LODH/LOC/PAR on hub deployments; here we add the foreign client).

INSERT INTO organisation (code) VALUES ('LODH') ON CONFLICT DO NOTHING;

INSERT INTO organisation (code) VALUES ('CGEG') ON CONFLICT DO NOTHING;

-- The remote hub row (required on the CGEG deployment so CGD.connectedHubCode FK resolves; a no-op
-- on the LODH deployment where V18 already created LOC).
INSERT INTO legal_entity (code, organisation_code, role, connected_hub_code)
VALUES ('LOC', 'LODH', 'TRADING_HUB', NULL)
ON CONFLICT DO NOTHING;

-- The remote client row: on CGEG this is the deployment's own TradingClient; on LODH this registers
-- CGD as a member of LOC's TradingClient list (cross-org membership spans organisations).
INSERT INTO legal_entity (code, organisation_code, role, connected_hub_code)
VALUES ('CGD', 'CGEG', 'TRADING_CLIENT', 'LOC')
ON CONFLICT DO NOTHING;

-- Demo MMXUser scope so the default frontend identity can act as CGD's ClientRepresentative on the
-- CGEG deployment (Settings only — CGD has no desk). No-op where demo-trader is already scoped.
INSERT INTO mmx_user_scope (user_id, legal_entity_code, role)
VALUES ('demo-trader', 'CGD', 'CLIENT_REPRESENTATIVE')
ON CONFLICT DO NOTHING;
