# Institution Settings API v1

Canonical OpenAPI: [openapi.yaml](./openapi.yaml). All trader operations require header `X-User-Id`.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/settings/institutions` | List catalog (may be empty); optional `?activeOnly=true` |
| POST | `/api/v1/settings/institutions` | Onboard institution (`displayName` only) |
| GET | `/api/v1/settings/institutions/{institutionCode}` | Get by code |
| POST | `/api/v1/settings/institutions/{institutionCode}/deactivate` | Deactivate |
| POST | `/api/v1/settings/institutions/{institutionCode}/activate` | Reactivate |

## InstitutionResponse

- `institutionCode` — system-generated immutable id (`{ACRONYM}-{nn}`)
- `displayName` — trader-supplied label
- `active` — when false, excluded from execute autocomplete and rejected at execute

## OnboardInstitutionRequest

- `displayName` (required, non-blank, max 128) — **must not** include `institutionCode`; server assigns code on `201 Created`.

## Errors

| Code | HTTP | When |
|------|------|------|
| `VALIDATION_ERROR` | 400 | Blank display name |
| `INSTITUTION_NOT_FOUND` | 404 | Unknown `institutionCode` |
| `INSTITUTION_SUFFIX_OVERFLOW` | 409 | Suffix would exceed `99` for acronym base |

## Role-scoped semantics (per `delegated-institution-grants`)

No new endpoints and no breaking schema changes; role scoping is an authorisation/behaviour layer on
the existing surface.

- **Trader on a TradingHub**: full native access — lists the hub's native institutions and onboards
  native institutions by `displayName` as above.
- **ClientRepresentative on a TradingClient**: Settings-only. The institution list (`GET
  /api/v1/settings/institutions`) returns **only** that client's thin-proxy institutions (derived
  name `{hubInstitution.displayName} via {hubLegalEntityCode}`); native hub institutions are excluded.
  Onboard (`POST /api/v1/settings/institutions`) is **role-qualified**: a ClientRepresentative
  onboards a **proxy** by selecting an active delegated grant (`hubInstitutionCode` in
  `OnboardInstitutionRequest`); the proxy `displayName` is derived and a free-form `displayName` is
  rejected. A proxy is not created unless an active grant exists for `(hubInstitution, client)` for at
  least one currency. Deactivate/activate on a proxy follow the same role scoping. See
  `contracts/006-delegated-institution-grants/api-v1.md` for the grant surface.
