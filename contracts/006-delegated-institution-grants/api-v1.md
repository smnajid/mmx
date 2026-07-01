# Delegated Institution Grants API v1

Canonical OpenAPI: [openapi.yaml](./openapi.yaml). All operations require header `X-User-Id` and an
active `(LegalEntity, role)` scope.

## Authorisation & scope

Grant CRUD is **Trader-only on the TradingHub**. The caller's active scope MUST be `(hubLegalEntity, Trader)`;
the active scope's LegalEntity is the granting hub. A `ClientRepresentative` receives `403` on hub grant CRUD.
`GET /api/v1/settings/delegated-grants/client` is **ClientRepresentative-only** on a TradingClient: lists active
grants for the caller's client `LegalEntityCode`. The hub institution referenced by a grant MUST be active, and
`enabledTenors` / `enabledNoticePeriods` MUST be subsets of the hub's managed-currency enabled sets for the currency.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/settings/delegated-grants` | List grants for the active hub (may be empty) |
| GET | `/api/v1/settings/delegated-grants/client` | List active grants for the active TradingClient (ClientRepresentative only) |
| POST | `/api/v1/settings/delegated-grants` | Create a grant (key `hubInstitutionCode` + `clientLegalEntityCode` + `currency`) |
| GET | `/api/v1/settings/delegated-grants/{hubInstitutionCode}/{clientLegalEntityCode}/{currency}` | Get a grant by key |
| PATCH | `/api/v1/settings/delegated-grants/{hubInstitutionCode}/{clientLegalEntityCode}/{currency}` | Update enabled tenors/notice periods |
| POST | `/api/v1/settings/delegated-grants/{hubInstitutionCode}/{clientLegalEntityCode}/{currency}/deactivate` | Deactivate (`active` false; no hard-delete) |
| POST | `/api/v1/settings/delegated-grants/{hubInstitutionCode}/{clientLegalEntityCode}/{currency}/reactivate` | Reactivate (`active` true) |

## DelegatedGrantResponse

- `hubInstitutionCode`, `clientLegalEntityCode` (`^[A-Z]{3}$`), `currency` (`^[A-Z]{3}$`), `active`
- `enabledTenors`: subset of `1W`, `2W`, `1M`, `3M`, `6M`, `1Y`
- `enabledNoticePeriods`: subset of `24H`, `48H`

## Semantics

- **Key**: `(hubInstitutionCode, clientLegalEntityCode, currency)`; one grant per tuple.
- **Independent of hub intake enablement**: a grant MAY enable a tenor/notice the hub keeps off for
  its own desk, and vice versa. The only bounds are the hub managed-currency enabled sets.
- **Prospective**: create/update/deactivate/reactivate do NOT alter already-routed or executed
  orders; historical orders keep the grant in effect when they were routed.
- **Deactivation is not deletion**: `active` toggles false; new TradingClient intake for the tuple is
  blocked, in-flight orders are untouched.

## Error codes

`VALIDATION_ERROR` (unknown/inactive hub institution, subset violation, invalid codes),
`GRANT_NOT_FOUND`, `DUPLICATE_GRANT`, `UNAUTHORIZED` (caller is not a Trader on the TradingHub).
