-- Idempotency key: unique per external system reference
CREATE UNIQUE INDEX idx_order_ext_ref
    ON money_market_order (external_order_reference);

-- Trader inbox queries: list by status + order type
CREATE INDEX idx_order_status_type
    ON money_market_order (status, order_type);

-- Assigned orders view: list by trader + status
CREATE INDEX idx_order_assigned_trader
    ON money_market_order (assigned_trader_id, status);

-- Value date range queries
CREATE INDEX idx_order_value_date
    ON money_market_order (value_date);

-- Audit log queries by order
CREATE INDEX idx_audit_order_id
    ON order_audit_log (order_id);

-- Audit log queries by time
CREATE INDEX idx_audit_event_time
    ON order_audit_log (event_time);
