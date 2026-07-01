# Managed Currency Settings API v1

Canonical OpenAPI: [openapi.yaml](./openapi.yaml). All trader operations require header `X-User-Id`.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/settings/currencies` | List catalog (may be empty) |
| POST | `/api/v1/settings/currencies` | Onboard currency |
| GET | `/api/v1/settings/currencies/{code}` | Get by ISO code |
| PATCH | `/api/v1/settings/currencies/{code}` | Update rules |
| POST | `/api/v1/settings/currencies/{code}/disable` | Deactivate |
| POST | `/api/v1/settings/currencies/{code}/enable` | Reactivate (set `active` true) |

## ManagedCurrencyResponse

- `code`, `active`, `minSubscriptionAmount`, `minIncreaseDecreaseAmount`
- `enabledTenors`: subset of `1W`, `2W`, `1M`, `3M`, `6M`, `1Y`
- `enabledNoticePeriods`: subset of `24H`, `48H`

At least one workspace MUST remain enabled on onboard/update: non-empty `enabledTenors` (Term) **or** non-empty `enabledNoticePeriods` (OnCall). Either set MAY be empty when the other is non-empty. PATCH: omit a field to leave it unchanged; send `[]` to clear that workspace.

## Role-scoped semantics (per `delegated-institution-grants`)

No new endpoints and no breaking schema changes; role scoping is an authorisation layer on the
existing surface.

- **Trader on a TradingHub**: full read/write — lists and manages the hub's managed-currency catalog.
- **ClientRepresentative on a TradingClient**: Settings-only and **read-only**. `GET` operations
  return the connected hub's managed-currency catalog (resolved in-process from the client's hub
  connection, ADR-0001). All mutation operations (`POST`, `PATCH`, `disable`, `enable`) return `403`
  — currencies are hub-owned. Client intake currency validation uses the hub's managed-currency set.
