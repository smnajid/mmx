-- Live contracts: executed subscriptions by portfolio and type
CREATE INDEX idx_order_portfolio_type_operation_status
    ON money_market_order (portfolio_number, order_type, order_operation, status);

-- Live contracts: redemption lookup by source contract
CREATE INDEX idx_order_source_contract_operation_status
    ON money_market_order (source_contract_number, order_operation, status);
