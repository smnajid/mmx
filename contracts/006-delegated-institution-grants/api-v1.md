# Delegated Institution Grants API v1

Canonical OpenAPI: [openapi.yaml](./openapi.yaml). All operations require header `X-User-Id` and an
active `(LegalEntity, role)` scope.

## Authorisation & scope

Grant CRUD is **Trader-only on the TradingHub**. The caller's active scope MUST be `(hubLegalEntity, Trader)`;
the active scope's LegalEntity is the granting hub. A `ClientRepresentative` receives `403` on hub grant CRUD.
`GET /api/v1/settings/delegated-grants/client` is **ClientRepresentative-only** on a TradingClient: lists active
grants for the caller's client `LegalEntityCode`. The hub institution referenced by a grant MUST be active, and
`enabledTenors` / `enabledNoticePeriods` MUST be subsets of the hub's managed-currency enabled sets for the currency.

**`503 Service Unavailable`** (`GET /api/v1/settings/delegated-grants/client`): on a TradingClient deployment whose hub is remote, a failed read of the client's grants from the hub (hub unreachable, timeout, credential rejected, or non-success response) returns `503` with body `{error: HUB_REFERENCE_DATA_UNAVAILABLE, message}`, never an empty list.

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
- **Granted, not usable**: an active grant makes the hub institution a **granted institution** for the
  client, eligible for institution onboarding (`contracts/004-institution-settings`). The client trades
  only through its own **onboarded institution** (derived name `"{hub name} via {hub LegalEntityCode}"`),
  and chooses its own **client enablement** within the grant. New business at the client uses the
  **effective enablement**: grant ∩ client enablement. A grant expansion never switches anything on
  at the client; a reduction caps the effective enablement without erasing the client's choice.
- **Deactivation is not deletion**: `active` toggles false. Deactivating a grant, or removing a
  tenor/notice period from it, makes the affected `(institution, client, currency, tenor|noticePeriod)`
  **closed to new business**: the client's new Subscription/Increase intake (and the hub's leg-A
  accept) is refused, while Decrease/Redemption against existing contracts is still accepted and
  routed. In-flight orders are untouched, and the client's onboarded institution (with its
  counterparty accounts) is never offboarded by a grant change.

## Error codes

`VALIDATION_ERROR` (unknown/inactive hub institution, subset violation, invalid codes),
`GRANT_NOT_FOUND`, `DUPLICATE_GRANT`, `UNAUTHORIZED` (caller is not a Trader on the TradingHub).
