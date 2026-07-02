-- Routed-order link columns (nullable for native desk orders)
-- H2 test profile: column adds only (partial unique indexes are PostgreSQL-specific; see bootstrap V21).
ALTER TABLE money_market_order ADD COLUMN routing_id UUID;
ALTER TABLE money_market_order ADD COLUMN originating_legal_entity_code VARCHAR(3);
ALTER TABLE money_market_order ADD COLUMN originating_external_order_reference VARCHAR(100);
