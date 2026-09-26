# Institution Settings API v1

Canonical OpenAPI: [openapi.yaml](./openapi.yaml). All operations require header `X-User-Id`; the
active `(LegalEntity, role)` comes from the session scope. Institution export (async):
[asyncapi.yaml](./asyncapi.yaml), prose [asyncapi-v1.md](./asyncapi-v1.md).

## Endpoints

| Method | Path | operationId | Description |
|--------|------|-------------|-------------|
| GET | `/api/v1/settings/institutions` | `listInstitutions` | List institutions in the caller's scope (may be empty); optional `?activeOnly=true` |
| POST | `/api/v1/settings/institutions` | `onboardInstitution` | Onboard (Trader: native by `displayName`; ClientRepresentative: granted institution by `hubInstitutionCode`). `201` on create, `200` on re-onboard |
| GET | `/api/v1/settings/institutions/granted` | `listGrantedInstitutions` | ClientRepresentative: granted institutions joined with onboarded ones |
| GET | `/api/v1/settings/institutions/{institutionCode}` | `getInstitution` | Get by code; owned by the active scope only, otherwise `404` |
| POST | `/api/v1/settings/institutions/{institutionCode}/deactivate` | `deactivateInstitution` | Trader: deactivate. ClientRepresentative: **offboard** |
| POST | `/api/v1/settings/institutions/{institutionCode}/activate` | `activateInstitution` | Trader: reactivate. ClientRepresentative: **re-onboard** (grant required) |
| PUT | `/api/v1/settings/institutions/{institutionCode}/counterparty-accounts` | `updateCounterpartyAccounts` | Replace the Term and OnCall counterparty accounts |
| PUT | `/api/v1/settings/institutions/{institutionCode}/enablement/{currency}` | `updateClientEnablement` | ClientRepresentative: replace the client enablement for one currency |

## InstitutionResponse

- `institutionCode` — system-generated immutable id (`{ACRONYM}-{nn}`)
- `displayName` — Trader-supplied label (native), or derived `"{hub displayName} via {hubLegalEntityCode}"` (onboarded)
- `active` — legacy flag; `false` exactly when `closedToNewBusiness` is `true`
- `closedToNewBusiness` — deactivated native institution or offboarded onboarded institution: Subscription/Increase refused, Decrease/Redemption still accepted (and executable)
- `termCounterpartyAccount`, `onCallCounterpartyAccount` — the owning LegalEntity's counterparty accounts; `null` when unset
- `hubLegalEntityCode`, `hubInstitutionCode` — onboarded institutions only: the linked hub-native institution
- `enablements[]` — onboarded institutions on single-institution responses only (empty on the list); one `ClientEnablementResponse` per currency that is granted or client-enabled:
  - `currency`
  - `grantedTenors`, `grantedNoticePeriods` — what the active grant enables (empty when the grant is inactive or absent)
  - `enabledTenors`, `enabledNoticePeriods` — the client enablement; a value enabled but not granted is shown as "enabled, not granted"

## CounterpartyAccount

A non-blank reference, trimmed, at most 34 characters, with no currency dimension. Each
`(LegalEntity, Institution)` holds at most one Term and one OnCall counterparty account, for native
and onboarded institutions alike. BNP at `LOC` and `BNP via LOC` at `PAR` hold independent accounts.

## OnboardInstitutionRequest

- `displayName` — Trader native onboard (required there, non-blank, max 128). **Must not** include `institutionCode`; the server assigns it. Rejected for a ClientRepresentative.
- `hubInstitutionCode` — ClientRepresentative institution onboarding of a granted institution. Mutually exclusive with `displayName`.
- `termCounterpartyAccount`, `onCallCounterpartyAccount` — optional.

Responses: `201` with the new entry; `200` when the ClientRepresentative onboards a hub institution
it had onboarded and then offboarded (the same record is reopened with its accounts intact; accounts supplied on the re-onboard request replace the stored ones, absent ones are kept).

## GrantedInstitutionResponse

- `hubLegalEntityCode`, `hubInstitutionCode`
- `displayName` — derived `"{hub displayName} via {hubLegalEntityCode}"`
- `currencies` — currencies with an active grant (an institution whose grants are all inactive is not listed)
- `onboardedInstitutionCode`, `closedToNewBusiness` — present only when the client has onboarded it

A remote (cross-Organisation) client reads its grants and the hub display names live from its hub;
its onboarded institutions are read from its own deployment.

## UpdateCounterpartyAccountsRequest

`termCounterpartyAccount`, `onCallCounterpartyAccount` — full replacement; `null` or absent clears
the account.

- The Trader maintains a TradingHub's native institutions; the ClientRepresentative maintains a TradingClient's onboarded institutions. An institution not owned by the active LegalEntity is `404`.
- Allowed while the institution is closed to new business.
- Clearing an account while any tenor (Term) or notice period (OnCall) is client-enabled on the institution, in any currency, is `409 COUNTERPARTY_ACCOUNT_IN_USE`.
- Saving the stored values again records no institution export.

## UpdateClientEnablementRequest

`enabledTenors`, `enabledNoticePeriods` — full replacement for `(institutionCode, currency)`.

- ClientRepresentative only; a Trader gets `403`.
- Opt-in: nothing is enabled right after onboarding.
- Every value newly switched on must be enabled by the current active grant for that currency (`400` otherwise); a value already enabled may be kept after the grant stops enabling it ("enabled, not granted").
- A Term tenor requires the Term counterparty account; an OnCall notice period requires the OnCall counterparty account (`400` otherwise).
- Stored in the client's own deployment (a remote client too) and never sent to the hub. Records no institution export.
- New business uses the **effective enablement**: grant ∩ client enablement, recomputed on every read. A grant reduction caps it without erasing the client's choice; a grant expansion never switches anything on.

## Errors

`InstitutionSettingsErrorResponse` (`error`, `message`):

| Code | HTTP | When |
|------|------|------|
| `VALIDATION_ERROR` | 400 | Blank display name, invalid counterparty account, enabling outside the grant or without the OrderType's account, onboarding without an active grant |
| `FORBIDDEN` | 403 | The active role may not perform the operation |
| `INSTITUTION_NOT_FOUND` | 404 | Unknown `institutionCode`, or not owned by the active LegalEntity |
| `INSTITUTION_SUFFIX_OVERFLOW` | 409 | Suffix would exceed `99` for acronym base |
| `INSTITUTION_ALREADY_ONBOARDED` | 409 | The client already holds an open onboarded institution for that hub institution |
| `COUNTERPARTY_ACCOUNT_IN_USE` | 409 | Clearing an account still used by client enablement |

## Role-scoped semantics (per `institution-onboarding`, `delegated-institution-grants`)

- **Trader on a TradingHub**: lists the hub's native institutions; onboards native institutions by
  `displayName` (optional accounts); deactivates/reactivates them (closed to new business for the
  hub's own intake and every client's routed intake; Decrease/Redemption still accepted); maintains
  their counterparty accounts. Deactivation and reactivation each record an institution export.
- **ClientRepresentative on a TradingClient**: Settings-only.
  - The list returns **only** that client's onboarded institutions (including offboarded ones, flagged
    `closedToNewBusiness`); native hub institutions are excluded. Same-Organisation and remote clients
    alike serve it from their own deployment.
  - A grant makes a hub institution a **granted institution**; onboarding makes it usable. The client
    holds at most one onboarded institution per hub institution; onboarding an open one again is `409`.
  - `deactivate` **offboards**: closed to new business, accounts kept, never deleted, idempotent;
    in-flight orders are untouched. `activate` **re-onboards** the same record and requires at least
    one active grant for the linked hub institution. A Trader cannot offboard or re-onboard a client
    institution.
  - Every onboarding, re-onboarding, offboarding, and account change records an institution export.
    Grant and client-enablement changes do not.

See `contracts/006-delegated-institution-grants/api-v1.md` for the grant surface.
