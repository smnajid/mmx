## Context

The order-creation widget (`frontend/projects/order-creation-widget/`) collects PM order data and emits `OrderCreationPayload` via `orderReady`. Per widget design D4, **submission is the host's job** — enrich with `externalOrderReference` and `POST /api/v1/orders`.

The dev playground (`frontend/src/app/features/widget-playground/`) already embeds the widget, logs events, and supports live-contract picking. It does not call intake. The canonical intake contract is `POST /api/v1/orders` (`ReceiveOrderRequest` in `specs/002-trader-orders-views/contracts/openapi.yaml`); no backend changes are required.

The playground configures an optional `apiBaseUrl` for widget order-creation calls. Intake must use the **same base** when set, otherwise a custom host would submit to the dev proxy while the widget reads another server.

`frontend/src/app/core/models/order.model.ts` `ReceiveOrderRequest` is stale (missing required `institutionCode`, still lists removed `desiredCounterpartyComment`).

## Goals / Non-Goals

**Goals:**

- Let developers send a logged `orderReady` payload to mmx intake from the playground with one explicit action.
- Map widget payload → intake request per OpenAPI (including `institutionCode`, `operation` → `orderOperation`).
- Show submit outcome (`orderId`, status code, or validation error) in the event log.
- Align frontend `ReceiveOrderRequest` with the published contract.
- Vitest coverage for mapping and submit flow (HttpTestingController).

**Non-Goals:**

- Backend, OpenAPI, or codegen changes.
- Widget library changes (no submit inside the library).
- Auto-submit on `orderReady`.
- Trader headers (`X-Trader-Id`) on intake — PM caller does not send them today.
- Deep-link to order detail after submit (optional nice-to-have; link text to `/orders/{id}` is acceptable if trivial).

## Decisions

### D1: Manual **Send to mmx** per `orderReady` event

Each event log entry with `type: 'orderReady'` gets a Send button. Clicking posts intake once; on success the button is replaced by a success line (orderId, HTTP status). On failure, show error message and keep Send enabled for retry with a **new** `externalOrderReference`.

**Rationale:** Preserves inspect-before-send; avoids flooding Received queues during wizard iteration. Matches host responsibility (PM decides when to submit).

**Alternative:** Auto-submit on `orderReady` — rejected (too easy to spam; harder to debug payload).

### D2: Pure mapping function `mapOrderCreationPayloadToReceiveRequest`

Add a small pure function (e.g. `widget-playground/order-creation-payload.mapper.ts`) taking `(payload, externalOrderReference)` → `ReceiveOrderRequest`:

| Widget field | Intake field |
|--------------|--------------|
| — | `externalOrderReference` (argument) |
| `orderType` | `orderType` |
| `operation` | `orderOperation` |
| `portfolioNumber` | `portfolioNumber` |
| `currency` | `currency` |
| `amount` | `amount` |
| `valueDate` | `valueDate` |
| `minimumRate?` | `minimumRate?` |
| `tenor?` | `tenor?` |
| `noticePeriod?` | `noticePeriod?` |
| `sourceContractNumber?` | `sourceContractNumber?` |
| `institutionCode` | `institutionCode` |
| `counterparty` | *(omit)* |

Unit-test the mapper in isolation (Vitest).

### D3: Playground posts via `HttpClient`, not `OrderApiService` as-is

Use `HttpClient.post(\`${apiBase}/api/v1/orders\`, body)` where `apiBase` is `activeApiBaseUrl()` trimmed or `''` (dev proxy). Do not use `OrderApiService.receiveOrder()` without base-url support — it hardcodes `/api/v1/orders`.

**Alternative:** Extend `OrderApiService` with optional base URL — rejected for scope; playground-only concern for now.

### D4: `externalOrderReference` generation

Format: `PLAYGROUND-{yyyyMMddHHmmss}-{4 alphanumeric}` (max 100 chars). Generated at Send time, displayed in the event row before/after submit. Re-send after failure generates a new reference.

Idempotent duplicate (200 with same reference) is unlikely in dev unless the user copies a reference; acceptable.

### D5: Event log model extension

Extend `PlaygroundEvent` with optional submit state:

```ts
submitStatus?: 'idle' | 'submitting' | 'success' | 'error';
submitResult?: { orderId: string; httpStatus: number };
submitError?: string;
externalOrderReference?: string;
```

Template: Send button when `orderReady` + `idle`; spinner when `submitting`; success/error lines otherwise.

### D6: Fix `ReceiveOrderRequest` TypeScript interface

Update `order.model.ts` to match OpenAPI: add required `institutionCode`, remove `desiredCounterpartyComment`. Grep for `desiredCounterpartyComment` usages in frontend tests/mocks and update.

## Risks / Trade-offs

- **[Risk] Stale frontend intake type causes wrong POST body.** → Fix `ReceiveOrderRequest` in same delivery; mapper tests assert `institutionCode` present.
- **[Risk] Custom `apiBaseUrl` mismatch.** → Intake uses same `activeApiBaseUrl()` as widget config panel.
- **[Risk] Intake validation failures (value date, inactive institution).** → Surface `400` `message` / `details` in event log; wizard already enforces T+2 client-side.
- **[Trade-off] No link to trader Received list.** → Document in playground hint; optional router link is low cost.

## Migration Plan

Frontend-only deploy. No DB or API migration. Playground remains dev-guarded (`isDevMode()`).

## Open Questions

(none — ready to implement)
