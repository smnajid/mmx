## Context

Portfolio Management (PM) creates Money Market orders via `POST /api/v1/orders`. Currently the PM system has no server-guided insight into which counterparties are available, what rates they offer, or which currency/tenor/notice-period combinations are viable. The counterparty (institution) is chosen by the trader at execution time.

This change introduces a set of **read-only REST endpoints** that the PM application consumes to build a wizard-style order creation flow. The wizard progressively narrows choices using mmx's existing reference data. Counterparty selection moves from trader execution to PM intake.

**Existing reference data already in mmx:**

| Data | Domain model | Table | Key |
|------|-------------|-------|-----|
| Managed currencies | `ManagedCurrency` | `managed_currency` | `code` |
| Active institutions | `Institution` | `institution` | `institution_code` |
| Term rates | `TermRate` → `TermRateAuditRow` | `term_rate` | `(trading_date, institution_code, currency, tenor)` |
| On-call rate segments | `OnCallRateSegment` | `oncall_rate_segment` | `(institution_code, currency, notice_period)` via `OnCallCurveKey` |

**Existing intake and execution:**
- `POST /api/v1/orders` — intake from PM, creates order in RECEIVED status
- `POST /api/v1/orders/{id}/execute` — trader provides `executedRate` + `institutionCode`

## Goals / Non-Goals

**Goals:**

- Expose nine new read-only GET endpoints under `/api/v1/order-creation/` for PM wizard consumption
- Cascading availability filtering: each step eliminates choices that have no downstream viable counterparty
- Rate transparency: counterparties show latest available rate with `rateDate` and `indicative` flag for stale rates
- Move counterparty (institution) selection from trader execution to PM intake
- Contract-first: OpenAPI and `api-v1.md` updated before any controller code
- TDD for all new behaviour

**Non-Goals:**

- No mmx frontend changes (wizard UI lives in the PM application)
- No new database tables or Flyway migrations (`institutionCode` column already exists on `money_market_order`)
- No rate computation or derived pricing — rates are shown as-is from reference data
- No PM authentication/authorization (out of scope for V1, same as current intake)
- No PositionApi integration for contract lookup (uses mmx's own executed Subscription orders instead)

## Decisions

### D1: Separate endpoints per wizard step (Option B)

Each wizard step gets its own endpoint rather than a single combined payload or a single endpoint with progressive query params.

**Rationale:** Separate endpoints are independently cacheable, have clear single-responsibility contracts, and match the PM wizard's step-by-step navigation model. The data volume is small (internal desk, handful of institutions/currencies) so the extra round-trips are negligible.

**Alternatives considered:**
- *Single "options tree" endpoint (Option C):* one call returning all currencies with nested tenors and counterparties. Simpler for the PM client but returns more data than needed at each step and couples all filtering into one service method.
- *Single endpoint with progressive params (Option A):* `GET /options?currency=EUR&tenor=3M` returning different shapes based on which params are present. Polymorphic response shape is harder to document in OpenAPI and test.

### D2: Counterparty rate fallback to latest available trading date

For Term counterparties, the system queries the latest `TermRate` per institution for the requested `(currency, tenor)` combination, not restricted to today's trading date. Each row includes `rateDate` (the trading date of the rate used) and `indicative` (true when `rateDate < today`).

**Rationale:** Prevents the wizard from being blocked when traders haven't uploaded today's rates yet. The PM sees stale rates with full transparency via the indicative flag and date. This matches standard financial reference data patterns where "latest known" is preferable to "no data."

**Alternatives considered:**
- *Today-only, empty if missing:* simpler query but blocks PM order creation until daily rate upload completes.
- *Configurable staleness window:* reject rates older than N days. Over-engineered for a POC; can be added later.

### D3: OnCall counterparties use segment covering PM's valueDate

For OnCall counterparties, the endpoint accepts the PM's chosen `valueDate` and finds the rate segment where `segment.valueDate <= valueDate <= segment.endDate` with status `VALID` or `PENDING_CONFIRMATION`. Both statuses are included because pending segments already price new orders per domain rules.

**Rationale:** On-call rate segments have date ranges. The rate applicable to the order depends on which segment covers the settlement date. Showing the correct segment's rate gives the PM accurate pricing guidance.

### D4: Contract-info lookup via executed Subscription orders

`GET /api/v1/order-creation/oncall/contract-info?contractNumber=X` queries mmx's `money_market_order` table for an executed Subscription with `generated_contract_number = X` and returns `currency` and `noticePeriod`.

**Rationale:** PositionApi is a modeled port not yet implemented. mmx already holds executed Subscription orders that allocated the contract number, so the data is available without external integration. This will be replaced by PositionApi in a later phase.

### D5: institutionCode moves to intake, removed from execute

`ReceiveOrderRequest` gains required `institutionCode`. The system validates the institution is active and derives `counterparty` from `Institution.displayName` at intake. `ExecuteOrderRequest` loses `institutionCode` — the counterparty is locked from intake. Trader must reject if they cannot deal with the PM-chosen counterparty.

**Rationale:** The PM wizard selects the counterparty based on rate information. Making this choice binding simplifies the trader's execution flow (rate-only) and prevents counterparty mismatch between PM intent and trader action.

**Breaking change strategy:** Both intake and execute request schemas change in the same delivery. The PM system upgrade (wizard) and mmx deploy are coordinated.

### D6: Hexagonal placement

| Concern | Module | Layer |
|---------|--------|-------|
| Cascading filter queries (available currencies, tenors, counterparties) | `mmx-application` | New `port/in` use case interfaces + services |
| New repository queries (latest term rate, open segments by currency, executed subscription lookup) | `mmx-application` `port/out` | Extended repository interfaces |
| REST controller for `/api/v1/order-creation/*` | `mmx-adapter-in-rest` | New `OrderCreationOptionsController` implementing generated `OrderCreationApi` |
| JPA query implementations | `mmx-adapter-out-persistence` | Extended Spring Data repositories |
| Intake institution validation | `mmx-application` | Modified `ReceiveOrderService` |
| Execute simplified (rate-only) | `mmx-application` | Modified `ExecuteOrderService` |
| Bean wiring | `mmx-bootstrap` | New/updated `@Configuration` |

No domain model changes — filtering is a query/read concern in the application layer.

### D7: OpenAPI contract structure

New paths added to `specs/002-trader-orders-views/contracts/openapi.yaml` under tag `OrderCreation`. New schemas: `CurrencyOption`, `OperationOption`, `TenorOption`, `NoticePeriodOption`, `CounterpartyOption`, `ContractInfoResponse`, and list wrappers. Codegen in `mmx-adapter-in-rest` generates `OrderCreationApi` interface; `OrderCreationOptionsController` implements it.

Breaking changes to existing schemas in the same delivery:
- `ReceiveOrderRequest`: add required `institutionCode`, remove `desiredCounterpartyComment`
- `ExecuteOrderRequest`: remove `institutionCode` (and legacy `counterparty`)

## Risks / Trade-offs

**[Risk] Stale rate data misleads PM** → Mitigated by `indicative` flag and `rateDate` on every counterparty row. PM UI can display warnings. Operational process should encourage timely rate uploads.

**[Risk] Contract-info lookup finds no matching Subscription** → Returns 404. PM app handles gracefully (contract may have been created outside mmx or before system adoption). This risk disappears when PositionApi is implemented.

**[Risk] Breaking changes to intake and execute schemas** → Coordinated deployment with PM system. No backward-compatible transition phase in this POC. If needed later, `institutionCode` on intake can be made optional with a deprecation path.

**[Trade-off] Cascading filters are eventually consistent with rate uploads** → The wizard reflects the latest state of reference data at query time. If a trader uploads new rates mid-wizard, the PM may see stale options. Acceptable for internal desk with low concurrency.

**[Trade-off] No caching layer** → Each wizard step hits the database. Acceptable for POC scale (single-digit concurrent users, small reference data sets). Can add Spring `@Cacheable` with short TTL later.
