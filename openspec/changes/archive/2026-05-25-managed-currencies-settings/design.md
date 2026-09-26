## Context

MMX today validates orders with **structural** domain rules on `MoneyMarketOrder` (order type/operation pairing, tenor vs notice presence, amount > 0, value date horizon) and **global** tenor/notice enums enforced at the REST/OpenAPI boundary. **Currency** is an unconstrained string; there is no trader-maintained catalog or per-currency business rules.

Traders need a **settings programme** starting with **managed currencies**: catalog onboarding, enabled tenors (Term) and notice periods (OnCall), and minimum amounts by operation. Rules must apply at **order boundaries**—Portfolio Management intake (`POST /api/v1/orders` per `specs/001-mm-order-processing`) and trader **amount updates** on assigned orders (`specs/002-trader-orders-views`).

**Decrease** validation additionally requires **open contract balance** from an external **PositionApi** (another team), exposing positions by `contractNumber` (and by counterparty for future use). MMX does not persist contract balances.

Exploration locked several product decisions (see **Decisions** below). Formal cross-cutting **ADRs** are deferred to later settings phases (institutions, rates).

## Goals / Non-Goals

**Goals:**

- Persist a **managed currency catalog** with per-currency configuration (enabled tenors, enabled notice periods, minimum subscription amount, minimum increase/decrease amount).
- Expose **contract-first** trader REST (`specs/003-managed-currency-settings/contracts/openapi.yaml` + `api-v1.md`) for list/onboard/update/disable.
- Enforce rules on **receive** and **update amount** via a hybrid domain policy + outbound ports.
- Integrate **PositionApi** for **Decrease** intake only: reject when `balance - decreaseAmount < minSubscription` for the order currency.
- **Strict cold start**: empty catalog ⇒ no new intake until traders onboard at least one currency.
- Angular **settings** area + header/shell navigation entry (`trader-desk-navigation` delta).
- TDD at domain, application, REST integration, and Vitest UI layers.

**Non-Goals:**

- Institution catalog, execution counterparty restrictions, rate tables.
- PositionApi lookups for **Increase** or **Redemption** in this change.
- Re-validating existing orders when configuration changes; changing duplicate-receive idempotency semantics.
- Formal ADR documents (capture patterns here and in specs first).

## Decisions

### 1. Validation architecture — hybrid (Option C)

**Decision:** Keep **structural** invariants on `MoneyMarketOrder.create` / update paths. Introduce **`OrderAgainstCurrencyPolicy`** in `mmx-domain` (pure Java) that evaluates desk rules given a loaded **`ManagedCurrency`** snapshot and, for Decrease, an **`OpenContractPosition`** snapshot.

**Orchestration:** `ReceiveOrderService` and `UpdateOrderService` in `mmx-application`:

1. Load configuration via **`ManagedCurrencyRepository`** port (out).
2. For `OrderOperation.DECREASE` on receive, load position via **`OpenPositionPort`** (out).
3. Invoke `OrderAgainstCurrencyPolicy.validate(...)` before aggregate mutation.
4. On failure, throw existing-style domain/application validation exceptions mapped to HTTP 4xx with field-level errors.

**Rationale:** Separates “order aggregate shape” from “desk configuration,” mirrors future institution rules, keeps Spring out of domain policy tests.

**Alternatives considered:**

- *All rules in application service* — faster to write but splits invariants across layers as settings grow.
- *Pass rules into `MoneyMarketOrder.create`* — couples aggregate factory to reference-data shape.

### 2. Outbound ports and adapters

| Port | Module | Responsibility |
|------|--------|----------------|
| `ManagedCurrencyRepository` | `mmx-application` `port.out` | CRUD/list; find by ISO code; active flag |
| `OpenPositionPort` | `mmx-application` `port.out` | `Optional<OpenContractPosition> findOpenByContractNumber(ContractNumber)` |
| JPA implementation | `mmx-adapter-out-persistence` | `managed_currency` (+ child rows or embedded flags) Flyway migration |
| PositionApi HTTP client | `mmx-adapter-out-integration` (extend) or `mmx-adapter-out-position` if client grows | Implements `OpenPositionPort`; config-driven base URL; **fail closed** on timeout/5xx |

**`OpenContractPosition` record (application or domain):** `contractNumber`, `currency` (ISO), `outstandingAmount` (`BigDecimal`, scale 2). Policy rejects if position currency ≠ order currency.

**PositionApi contract:** Owned by external team; mmx consumes their OpenAPI when available. Until then: **in-memory/fake adapter** for tests and local dev; WireMock or stub in integration tests.

### 3. Cold start — strict empty catalog

**Decision:** Zero rows in `managed_currency` ⇒ **all** intake rejected (unknown currency). No Flyway seed of tradable currencies in production path.

**Rationale:** Forces explicit desk setup; avoids accidental trading on defaults.

**Implication:** `scripts/seed-demo-orders.sh` and quickstarts must **onboard currencies via settings API** (or documented bootstrap curl) before posting orders.

### 4. Configuration lifecycle

| Action | Behaviour |
|--------|-----------|
| Onboard currency | Validate **ISO 4217** code; **reject duplicates** |
| Update rules | At least **one** tenor enabled (Term set) and **one** notice enabled (OnCall set)—API/UI **block** disabling the last enabled code |
| Disable currency | Set `active = false` (or equivalent); **allowed** while Received/Assigned orders exist for that currency |
| Effect of disable | **No new intake** for that currency; existing orders continue; trader updates still run through policy (clarify in specs: updates rejected if currency inactive) |

### 5. Rule change semantics (as proposal)

- **New intake / amount update:** Evaluated against **current** configuration and PositionApi balance.
- **Orders already in queues:** Not re-validated when toggles change.
- **Duplicate receive:** Unchanged—return existing order, ignore payload (`ReceiveOrderService`).

### 6. Amount minimums and Decrease

| Operation | Minimum field |
|-----------|----------------|
| Subscription | `minSubscriptionAmount` |
| Increase, Decrease | `minIncreaseDecreaseAmount` |
| Redemption | Same floor as Increase/Decrease |

**Decrease additional rule:** Let `B` = PositionApi outstanding for `sourceContractNumber`, `A` = order amount, `S` = `minSubscriptionAmount`. Reject if `B - A < S` (also require `A` meets increase/decrease minimum and currency/notice rules).

**Increase / Redemption:** No PositionApi call in this change.

### 7. HTTP surfaces (contract-first)

**New feature folder:** `specs/003-managed-currency-settings/`

- `contracts/openapi.yaml` — canonical; codegen into `mmx-adapter-in-rest` (new tag/interface, e.g. `CurrencySettingsApi`).
- `contracts/api-v1.md` — prose mirror.

Suggested operations (names finalized in spec): list currencies, get by code, create (onboard), patch rules, disable.

**Existing contracts:** Delta `specs/001-mm-order-processing` FRs for currency catalog validation, disabled tenor/notice, amount floors, Decrease position rule, error codes. Intake OpenAPI may add problem-detail examples only—no new PM fields if balance comes from PositionApi.

**Trader desk:** `specs/002-trader-orders-views` unchanged for queue paths; shell navigation per `openspec/specs/trader-desk-navigation` delta.

### 8. Persistence model (initial)

```
managed_currency
  code              CHAR(3) PK
  active            BOOLEAN NOT NULL
  min_subscription  DECIMAL(18,2) NOT NULL
  min_lifecycle     DECIMAL(18,2) NOT NULL   -- Increase/Decrease/Redemption
  -- enabled tenors: either 6 boolean columns or child table (currency, tenor_code, enabled)
  -- enabled notices: 24H, 48H flags
```

Prefer **explicit boolean columns** for six tenors + two notices in v1 (simple queries, matches fixed enum sets in domain). Normalize later if settings programme adds dimensions.

### 9. Application services

| Service | Role |
|---------|------|
| `ManageCurrencySettingsService` | Implements inbound ports for settings CRUD; enforces last-tenor/notice guard on write |
| `ReceiveOrderService` | Inject policy + ports; validate before `MoneyMarketOrder.create` |
| `UpdateOrderService` | Same policy on amount changes |

Optional: `GetCurrencySettingsUseCase` for trader UI list/detail.

### 10. Frontend (Angular)

- New feature `features/currency-settings/` (list + edit).
- **Header link** “Settings” or “Currencies” in `app.html`—orthogonal to ON-CALL/Term desk tabs (does not alter two-tier queue model).
- Generated or hand-maintained client from `003` OpenAPI; Vitest for forms (tenor/notice toggles, min amounts, last-enabled disable).

### 11. Testing strategy (TDD)

| Layer | Focus |
|-------|--------|
| `OrderAgainstCurrencyPolicyTest` | Matrix: unknown currency, disabled tenor, below mins, decrease below subscription floor |
| `ReceiveOrderServiceTest` / `UpdateOrderServiceTest` | Mock ports; red-first for new rejections |
| `FakeOpenPositionPort` | Deterministic balances |
| REST integration | Settings API + intake rejection cases |
| Position adapter | Contract test against stub server when external OpenAPI available |

### 12. Documentation

- OpenSpec capability specs under `openspec/changes/managed-currencies-settings/specs/`.
- Feature `specs/003-*` and `001` FR deltas delivered with implementation.
- **ADR:** Not in this change; revisit when **institutions** phase starts (reference-data + external integration patterns).

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| PositionApi unavailable at intake | **Fail closed** (reject Decrease with clear error); document in spec; monitor adapter timeouts |
| PositionApi contract drift | Versioned client from their OpenAPI; adapter integration tests |
| Strict cold start breaks demos | Update seed/quickstart to onboard currencies first |
| Policy + aggregate duplication | Single policy class; no second copy of tenor-required rules |
| Disable currency with open Received orders | Allowed by design; traders must clear or execute; no new intake |
| External team dependency | Ship fake port + stub; swap HTTP client when API ready |

## Migration Plan

1. Flyway: `managed_currency` tables; deploy with **empty** catalog (intake blocked until setup).
2. Deploy settings API + trader UI; traders onboard required currencies.
3. Deploy receive/update policy + PositionApi adapter (stub in non-prod if needed).
4. Update demo seed scripts and `quickstart.md` for currency onboarding step.

**Rollback:** Revert application release; DB migration rollback only if no production currencies configured yet—otherwise leave tables and disable feature flag/policy wiring if needed.

## Open Questions

- **PositionApi:** Exact path, auth, and field names from owning team—blocking only the real HTTP adapter, not policy/tests.
- **Inactive currency + assigned order update:** Confirm reject all updates vs amount-only (default in design: **reject** mutations that fail policy, including inactive currency).
- **Feature number:** Use `003-managed-currency-settings` unless product renumbers.
