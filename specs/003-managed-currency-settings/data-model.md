# Data model — Managed currency settings

## ManagedCurrency (persisted)

| Field | Type | Notes |
|-------|------|-------|
| `code` | CHAR(3) PK | ISO 4217 uppercase |
| `active` | BOOLEAN | false = no new intake |
| `min_subscription_amount` | DECIMAL(18,2) | Subscription floor |
| `min_increase_decrease_amount` | DECIMAL(18,2) | Increase, Decrease, Redemption floor |
| `tenor_1w` … `tenor_1y` | BOOLEAN | Enabled flags for standard tenors |
| `notice_24h`, `notice_48h` | BOOLEAN | Enabled notice periods |

## OpenContractPosition (external, not persisted)

Loaded from PositionApi by `contractNumber` for Decrease validation only.

| Field | Type |
|-------|------|
| `contractNumber` | string |
| `currency` | ISO code |
| `outstandingAmount` | DECIMAL(18,2) |
