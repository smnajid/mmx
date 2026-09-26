-- Align with money_market_order.currency (VARCHAR(3)) and JPA @Column(length = 3) mapping.
ALTER TABLE managed_currency
    ALTER COLUMN code TYPE VARCHAR(3);
