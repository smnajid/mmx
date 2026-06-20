# Institution Settings API v1

Canonical OpenAPI: [openapi.yaml](./openapi.yaml). All trader operations require header `X-Trader-Id`.

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
