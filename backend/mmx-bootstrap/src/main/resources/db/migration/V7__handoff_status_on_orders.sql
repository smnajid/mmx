-- handoff_status: integration visibility for EXECUTED rows (FR-013).
-- Backfill: existing EXECUTED orders assumed already handed off under prior sync gateway → PUBLISHED.
ALTER TABLE money_market_order
    ADD COLUMN handoff_status VARCHAR(20) NULL;

UPDATE money_market_order
SET handoff_status = 'PUBLISHED'
WHERE status = 'EXECUTED'
  AND handoff_status IS NULL;
