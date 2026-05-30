# Data model — Institution settings

## Institution (persisted)

| Field | Type | Notes |
|-------|------|-------|
| `institution_code` | VARCHAR(32) PK | System-generated, e.g. `HSBC-01`, `BCI-02` |
| `display_name` | VARCHAR(128) NOT NULL | Trader label; copied to execution `counterparty` |
| `active` | BOOLEAN NOT NULL | false = excluded from execute autocomplete |
| `created_at` | TIMESTAMPTZ NOT NULL | |
| `updated_at` | TIMESTAMPTZ NOT NULL | |

## Order execution audit (optional column on `money_market_order`)

| Field | Type | Notes |
|-------|------|-------|
| `institution_code` | VARCHAR(32) NULL | Set at execute from selected catalog row |

## Institution (domain snapshot)

| Field | Type |
|-------|------|
| `institutionCode` | string |
| `displayName` | string |
| `active` | boolean |
