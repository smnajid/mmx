## Context

**Current state**

- Intake (`ReceiveOrderService`) persists `institutionCode` and derives `counterparty` from `Institution.displayName` at reception.
- `OrderDetailsResponse` (OpenAPI) exposes `institutionCode` and `counterparty` before execution; `OrderRestMapper.toDetails` maps both.
- `ExecuteOrderService` accepts only `executedRate`; counterparty comes from the order's intake institution.
- Frontend `OrderExecutionFormComponent` still loads the institution catalog, shows a counterparty datalist, and emits `{ executedRate, institutionCode }` — behaviour from the superseded `institution-onboarding-rates` era.
- Frontend `OrderDetails` TypeScript interface omits `institutionCode`; the detail grid hides counterparty until EXECUTED.
- PM wizard already fetches indicative rates via `GET /api/v1/order-creation/{term|oncall}/counterparties` with `indicative` and `rateDate` on each row.

**Constraints**

- Contract-first: no new REST endpoints; reuse published order-creation counterparties APIs.
- Hexagonal backend unchanged — frontend-only delivery.
- TDD: failing Vitest specs before component changes.

## Goals / Non-Goals

**Goals:**

- Trader sees PM-chosen counterparty on order detail and execute form without re-selecting.
- Execute POST body matches OpenAPI (`{ executedRate }` only).
- Executed rate field pre-filled from the latest segment/term rate for the intake institution, with indicative transparency.
- Vitest coverage for rate-only emit, locked counterparty display, and indicative pre-fill.

**Non-Goals:**

- New trader-specific rate API (order-creation endpoints are read-only and unauthenticated today — acceptable for POC; document if auth is added later).
- Showing full counterparty rate tables on order detail (only the single intake institution's proposed rate on the execute panel).
- Changing reject/update/assign action policy.

## Decisions

### D1: Pass full `OrderDetails` into the execution form

`OrderExecutionFormComponent` receives `[order]="o"` from `OrderDetailsComponent` instead of loading institutions independently.

**Rationale:** Counterparty, `institutionCode`, `orderType`, `currency`, `tenor`, `noticePeriod`, `valueDate`, and `minimumRate` are all on the loaded order. Removes `InstitutionSettingsApiService` dependency from execute form.

**Alternative:** Keep institution picker pre-selected from order — rejected; contradicts `order-institution-constraints` and confuses traders who think they can change counterparty.

### D2: Rate-only `ExecuteOrderRequest`

Update `frontend/src/app/core/models/order.model.ts`:

```ts
export interface ExecuteOrderRequest {
  executedRate: number;
}
```

`OrderExecutionFormComponent` emits `{ executedRate }` only.

**Rationale:** Matches `specs/002-trader-orders-views/contracts/openapi.yaml` `ExecuteOrderRequest` and backend `ExecuteOrderCommand`.

### D3: Indicative rate via existing order-creation counterparties endpoints

Add `OrderCreationApiService` (or extend an existing API service) in `frontend/src/app/core/api/` with:

| Order type | Endpoint | Query params |
|------------|----------|--------------|
| `TERM` | `GET /api/v1/order-creation/term/counterparties` | `currency`, `tenor` |
| `ON_CALL` | `GET /api/v1/order-creation/oncall/counterparties` | `currency`, `noticePeriod`, `valueDate` |

On `ngOnInit` (when `order` input is set), call the appropriate endpoint, find `counterparties.find(c => c.institutionCode === order.institutionCode)`, and set `rateModel` to `String(rate)` when found.

Show below the rate input:

- `rateDate` (mono)
- **Indicative** badge when `indicative === true` (reuse PM wizard styling pattern)

If no matching row (rate segment missing, institution deactivated since intake), leave rate empty and show a short hint — execute remains possible if the trader enters a rate manually.

**Rationale:** Same rate source PM used at order creation; no duplicate rate logic in the trader app.

**Alternative:** Store proposed rate on the order at intake — rejected; rate can change between intake and execute; lookup at execute time is fresher.

### D4: Show counterparty on detail grid before execution

Add to `order-details.component.ts` template (for any status where `o.counterparty` is present):

```html
<dt>Counterparty</dt>
<dd>{{ o.counterparty }} <span class="mono muted">{{ o.institutionCode }}</span></dd>
```

Remove dependency on `desiredCounterpartyComment` for counterparty display (field is removed from intake; keep rendering only if API still returns it for legacy rows).

### D5: Minimum rate hint on execute form

When `order.minimumRate != null`, show read-only "PM floor: X%" near the rate input (detail grid already shows minimum rate; optional duplicate on form for execute context).

**Rationale:** Trader must not submit below floor; inline hint reduces 400 surprises.

### D6: Spec deltas, not feature OpenAPI changes

Update OpenSpec capability specs and `specs/002-trader-orders-views/spec.md` FR-003a prose in the same delivery. No `openapi.yaml` edit.

## Risks / Trade-offs

- **[Risk] Order-creation endpoints lack `X-Trader-Id`** → Acceptable for POC; counterparties are reference data. If auth is added later, wire trader headers in `OrderCreationApiService`.
- **[Risk] Rate lookup fails (404/empty)** → Mitigated: empty pre-fill + hint; trader enters rate manually; execute unchanged.
- **[Risk] `institutionCode` missing on old frontend model** → Add to `OrderDetails` interface; API already returns it.
- **[Trade-off] Term-rate specs previously forbade rates on execute** → Narrowed to "no rate management/browse UI on order screens"; single proposed rate on execute form is explicitly allowed via spec delta.
- **[Trade-off] Stale `trader-order-detail-actions` archived in openspec/specs** → REMOVED + ADDED requirements in delta; archive merges fix main spec.

## Migration Plan

Frontend-only deploy. No database migration. Traders on ASSIGNED orders immediately see locked counterparty; no data backfill required.

## Open Questions

(none — ready to implement)
