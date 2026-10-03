# Proposal

## Why

A Portfolio Manager of a cross-organisation TradingClient (CGD on the CGEG deployment) cannot create any order: the wizard's currency step always shows "No currencies are currently available for order creation." The remote-backed rate adapters on a client deployment return nothing (Term) or read an empty local table (OnCall), and a hub outage is silently rendered as "nothing available". Independently, the currency/tenor/notice-period steps apply the hub's **own intake enablement** to clients, contradicting the domain rule that a TradingClient is bounded by its **effective enablement** (see `CONTEXT.md`, *Grant vs hub own-intake enablement*). The existing `order-routing` requirement "Thin remote client reads hub reference data live" already promises a populated order-creation form for a remote client; the code does not meet it.

## What Changes

- **Client option rule.** For a TradingClient (local such as PAR, or remote such as CGD), the order-creation currencies, tenors and notice periods SHALL be derived from its **onboarded institutions open to new business** whose **effective enablement** (grant ∩ client enablement) permits the term, and which have a current hub rate. The hub's own intake enablement no longer bounds a client. TradingHub behaviour is unchanged.
- **BREAKING** `legalEntityCode` becomes a **required** query parameter on `GET /api/v1/order-creation/{term,oncall}/currencies`, `/term/tenors` and `/oncall/notice-periods` (as it already is on counterparties). The embeddable widget already holds `legalEntityCode` and is updated in the same delivery.
- **Hub rate reads for remote clients.** The hub deployment exposes new cross-org reference reads: latest Term rate per institution for `(currency, tenor)`, and OnCall rate segments for `(currency, noticePeriod)` — open segments, or the segments covering a `valueDate`. The client deployment implements its rate read ports against them; nothing is replicated (ADR 0007).
- **Grant-scoped hub rates (BREAKING for the cross-org contract).** Every cross-org rate read, including the existing `GET /api/v1/cross-org/reference/term-rates`, SHALL return only rows for `(institution, currency)` pairs granted to the transport-proven client.
- **Hub failure is an error, not an empty answer.** On a client deployment, any remote reference-data read that fails (hub unreachable, timeout, credential rejected, non-200) SHALL surface as `503 Service Unavailable` on the client's own API, instead of an empty list. The widget's existing error/Retry state and the Settings screens' existing error banners render it.
- Current rate definitions are unchanged and identical on hub and client: Term = latest uploaded rate per institution; OnCall = segment in `VALID` or `PENDING_CONFIRMATION`.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `pm-order-creation-options`: currencies, tenors and notice periods take `legalEntityCode` and follow effective enablement for a TradingClient; endpoints return `503` when a TradingClient's hub reference data is unreachable.
- `order-routing`: thin remote client reads now include grant-scoped hub rate reads (Term latest-per-institution, OnCall segments) and fail loudly when the hub is unreachable.

## Impact

- **Backend (Spring Boot 4, hexagonal; strict TDD, red first):**
  - `mmx-application`: client branch in `TermOrderCreationOptionsService` / `OnCallOrderCreationOptionsService` (currencies, tenors, notice periods) reusing `EffectiveEnablement`; new `HubReferenceDataUnavailableException`; extended `listCurrencies` / `listTenors` / `listNoticePeriods` use-case signatures.
  - `mmx-adapter-out-integration`: `RemoteReferenceDataHttp` throws instead of returning empty; `RemoteTermRateRepository` completed; new read-only `RemoteOnCallRateRepository`.
  - `mmx-adapter-in-rest`: `OrderCreationOptionsController` passes `legalEntityCode`; `CrossOrgReferenceDataController` grant-scopes rate reads and serves the new reads; `GlobalExceptionHandler` maps the new exception to `503`.
  - `mmx-bootstrap`: wire `RemoteOnCallRateRepository` under `mmx.cross-org.reference-data-remote=true`.
- **Contracts (contract-first, codegen):** `contracts/002-trader-orders-views/openapi.yaml` + `api-v1.md` (required `legalEntityCode`, `503` responses); `contracts/007-cross-org-routing/openapi.yaml` + `api-v1.md` (new rate reads, grant scoping).
- **Frontend (Angular 21):** order-creation widget `WizardApiService` and step components send `legalEntityCode`; regenerate widget API types. Settings already render HTTP errors — verified, not rebuilt.
- **Docs:** `CONTEXT.md` already updated (client rule, CGEG name). No new ADR (follows ADR 0007).
