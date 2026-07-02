-- Routed-order link columns (nullable for native desk orders)
ALTER TABLE money_market_order ADD COLUMN routing_id UUID;
ALTER TABLE money_market_order ADD COLUMN originating_legal_entity_code VARCHAR(3);
ALTER TABLE money_market_order ADD COLUMN originating_external_order_reference VARCHAR(100);

CREATE UNIQUE INDEX uq_money_market_order_routing_id_client
    ON money_market_order (routing_id)
    WHERE originating_legal_entity_code IS NULL AND routing_id IS NOT NULL;

CREATE UNIQUE INDEX uq_money_market_order_routing_id_hub
    ON money_market_order (routing_id)
    WHERE originating_legal_entity_code IS NOT NULL AND routing_id IS NOT NULL;
