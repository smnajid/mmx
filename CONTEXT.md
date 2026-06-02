# Money Market Exchange (mmx)

Internal application for Money Market **order intake and trader workflow** (assign, update, execute, cancel, reject) plus **reference-data settings** (currencies, institutions, term rates, on-call rate curves). Market dealing happens outside the system; execution records the outcome.

Agents: use this file for **domain vocabulary**. For **where code lives**, read [docs/agents/codebase-map.md](docs/agents/codebase-map.md).

## Language

### Orders and lifecycle

**Money Market order** (aggregate **MoneyMarketOrder**):
A single order received from Portfolio Management and processed by traders through to execution (and optionally accounting handoff).
_Avoid_: deal, trade, transaction (ambiguous with market dealing).

**OrderType**:
Product mode — **Term** or **OnCall**. Term and OnCall orders MUST NOT appear mixed on the same desk queue surface.
_Avoid_: workspace (use for UI navigation only), product line.

**OrderOperation**:
What the order does — **Subscription**, **Increase**, **Decrease**, or **Redemption**. Allowed operations depend on OrderType (Term: Subscription only; OnCall: all four).
_Avoid_: action, event type.

**OrderStatus**:
Lifecycle state — **Received**, **Assigned**, **Executed**, **Cancelled**, **Rejected**, and (after back-office accounting) **Accounted**.
_Avoid_: stage, phase.

**Received**:
Order accepted from intake; not yet assigned to a trader.

**Assigned**:
Order assigned to exactly one **Trader**; only that trader may update or execute.

**Executed**:
Trader recorded execution outcome (rate, institution/counterparty, system-generated references). Market dealing already happened externally.

**Cancelled** / **Rejected**:
Withdrawn (Received only) vs refused by trader (Received or Assigned); rejection requires a reason.

**Trader**:
Internal user operating the desk. V1 has a single role; identity is carried on API requests (e.g. `X-Trader-Id`).
_Avoid_: user, operator (too generic).

**Assignment**:
Link between one Trader and one order. Cleared on unassign (returns to Received).

**ExternalOrderReference**:
Portfolio Management's idempotency key; duplicate intake MUST NOT create a second order.

**ContractNumber**:
Identifies an on-call contract for lifecycle operations. Subscription execution allocates a new number; Increase/Decrease/Redemption reuse intake **sourceContractNumber**.

**DealingReference**:
System-generated reference at execution.

**ExecutionDetails**:
ExecutedRate, counterparty (from selected institution at execute), ExecutionTime, DealingReference, and execution contract number rules per operation.

**MinimumRate**:
Optional PM floor at intake; when present, ExecutedRate MUST meet or exceed it. Traders MUST NOT change it after intake.

**Tenor** (Term only):
Fixed maturity bucket — 1W, 2W, 1M, 3M, 6M, 1Y — must be enabled for the order currency.

**NoticePeriod** (OnCall only):
Call notice — **24H** or **48H** — must be enabled for the order currency.
_Avoid_: notice (alone), call period.

**ValueDate**:
Settlement date; intake requires at least two calendar days in the future.

### Desk navigation (trader UI)

**Desk**:
Trader order queues (Term / OnCall × Received / Assigned / Executed), distinct from **Settings**.

**Primary tab**:
**ON-CALL** or **Term** — selects OrderType workspace. Labels MUST NOT include the word "Workspace".

**Sub-tab** / **queue**:
**Received**, **Assigned**, or **Executed** within the active primary tab.

**Settings**:
Reference-data area under `/settings/*` (currencies, institutions, term rates, on-call rates). Desk tabs are hidden on settings routes.

### Accounting handoff (executed orders)

**HandoffStatus** (on executed orders, separate from OrderStatus):
Outbound progress to back office — **PENDING**, **PUBLISHED**, **ACCOUNTED** (and related transitions in domain code).

**Accounted**:
OrderStatus after back office confirms portfolio impact; executed desk lists exclude Accounted rows (see feature 002).

_Avoid_: synced, posted (unless quoting external systems).

### Reference data

**Managed currency**:
Catalog entry controlling which currencies and tenors/notice periods are valid for intake and trader updates.

**Institution**:
Onboarded financial institution (code, display name, suffix rules). At execute, trader selects **institutionCode**; response **counterparty** is derived from institution display name (not free text).
_Avoid_: counterparty (for execute request body — legacy free-text is removed).

**Term rate**:
Daily uploaded rate row keyed by institution, currency, tenor, and trading date.

**OnCall rate curve point**:
Identified by **(institution, currency, noticePeriod)**. Holds ordered **rate segments**.

**Rate segment** (**OnCallRateSegment**):
Portion of a curve with `segmentId`, `rate`, inclusive `valueDate`, inclusive end date, and segment **status**.

**Open segment**:
Current segment ending on sentinel date **2999-12-31** (no-end).

**PENDING_CONFIRMATION** (segment status):
New rate added by trader; prices new orders immediately; awaits back-office confirmation.

**VALID** / **CANCELED** (segment status):
Confirmed by back office vs trader/system cancel before confirmation.

### Integration boundaries

**Portfolio Management** (intake):
External source of orders via REST intake; only source in V1.

**Back office**:
Downstream consumer of execution and on-call rate handoff messages (outbox / callbacks); confirms accounting and rate segments.

**Deposits** / **PositionApi**:
Modeled ports for contract balance checks (e.g. Decrease validation); not full product scope in V1.

## Relationships

- A **Money Market order** has one **OrderType**, one **OrderOperation**, and one **OrderStatus** at a time.
- **Assignment** exists only while status is **Assigned**.
- **Term** orders use **Tenor**; **OnCall** orders use **NoticePeriod** — never both on the same order.
- **Managed currency** constrains which **Tenor** / **NoticePeriod** values are allowed per currency.
- **Institution** is required at **execute**; display name becomes **counterparty** on the order.
- An **OnCall rate curve point** has many **rate segments** over time; at most one **PENDING_CONFIRMATION** segment per curve point.
- **HandoffStatus** applies after **Executed** for back-office messaging; **Accounted** is a terminal **OrderStatus**.

## Example dialogue

> **Dev:** "Can a trader execute an OnCall **Decrease** without picking an institution?"
> **Domain expert:** "No — execute requires an active **institutionCode**. **Counterparty** on the response comes from that institution's display name."
>
> **Dev:** "We added a new on-call rate but back office hasn't confirmed — can we add another for the same currency?"
> **Domain expert:** "Not while a **PENDING_CONFIRMATION** segment exists on that **curve point** `(institution, currency, noticePeriod)`. Wait until it becomes **VALID** or **CANCELED**."

## Flagged ambiguities

- **OnCall** vs **ON_CALL**: UI routes and labels use `oncall` / **ON-CALL**; Java enums and API enums often use `ON_CALL`. Same concept.
- **Workspace**: acceptable in developer docs for "Term vs OnCall area"; trader-facing UI MUST NOT use "Workspace" in tab labels (feature 002).
- **Counterparty**: at execution, means the institution's display name on the order record, not a separate free-text field.

## Further reading

- Authoritative order rules: [specs/001-mm-order-processing/spec.md](specs/001-mm-order-processing/spec.md)
- Desk views and Accounted: [specs/002-trader-orders-views/spec.md](specs/002-trader-orders-views/spec.md)
- On-call curve rules: [openspec/specs/oncall-rate-curve-management/spec.md](openspec/specs/oncall-rate-curve-management/spec.md)
- ADRs (when present): [docs/adr/](docs/adr/)
