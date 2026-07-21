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
Lifecycle state — **Received**, **Routed**, **Assigned**, **Executed**, **Cancelled**, **Rejected**, and (after back-office accounting) **Accounted**. `Routed` applies only to a **client-side order** that has been handed to a **TradingHub**; a hub-side order never uses `Routed`.
_Avoid_: stage, phase.

**Received**:
Order accepted from intake; not yet assigned to a trader.

**Assigned**:
Order assigned to exactly one **Trader**; only that trader may update or execute.

**Executed**:
Trader recorded execution outcome (rate, institution/counterparty, system-generated references). Market dealing already happened externally.

**Cancelled** / **Rejected**:
Withdrawn (Received only) vs refused by trader (Received or Assigned); rejection requires a reason.

**MMXUser**:
Any human who logs into an MMX instance. Identified on requests by **`X-User-Id`** (umbrella identity, replacing the legacy `X-Trader-Id`). One MMXUser per human; a user holds **N allowed scopes**, each a **`(LegalEntity, role)`** pair (e.g. one person may be a ClientRepresentative on both `PAR` and `SIN`). Authorisation is driven by the user's **active scope** (see *Entity scope of a session*). Adding future human roles is a new role value, not a new identity header.
_Avoid_: user (alone — too generic), operator.

**Role** (of an MMXUser):
What an MMXUser may do on the LegalEntity they are scoped to. V1 roles: **Trader** or **ClientRepresentative**.

**Entity scope of a session**:
A session is bound to exactly **one active scope** `(LegalEntity, role)`, chosen at login from the user's allowed scopes. Every request is authorised and filtered against that one active scope — the server never accepts a free per-request entity choice. A user with multiple scopes may **switch** active scope within the session via a re-scope (the server re-validates the user holds the requested `(LegalEntity, role)` and rebinds the session); this is not a full re-authentication.

**Trader**:
Role for an MMXUser who operates the desk — assigns, updates, executes, cancels, rejects orders. Only meaningful on a **TradingHub**. Carried on requests as `X-User-Id` with role Trader. (Was the single user type in V0, identified by `X-Trader-Id`.)
_Avoid_: user, operator (too generic).

**ClientRepresentative**:
Role for an MMXUser who represents a **TradingClient**. Sees **Settings** only — never the Desk. **Settings scope:** manage **delegated institution grants** (enable/disable tenors/notice periods per `(institution, currency)`, within what the hub granted); read-only view of currencies (fetched from the connected hub) and of rates for granted institutions. Cannot edit managed currencies, upload term rates, manage on-call curves, or natively onboard institutions.
_Avoid_: client user, settings user, onboarding officer.

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
OrderStatus after back office confirms portfolio impact; executed desk lists exclude Accounted rows (see feature 002). Semantically: the booking system (Transactions 2) has confirmed the booking that the back office sent after contract validation, so the order is now in the accounting position. Reaching `Accounted` is what lets MMX **remove the order from the PM-facing list of orders not yet added to the accounting position**. For a routed order, `Accounted` is reached separately on the hub-side and client-side orders (each side has its own contract, its own booking, its own Transactions 2 confirmation).

_Avoid_: synced, posted (unless quoting external systems).

### Order routing (TradingClient → TradingHub)

**Routed order**:
An order placed by a **TradingClient**'s Portfolio Management on a delegated Fiduciary institution, which MUST be executed at the connected **TradingHub**. Routing is in-process within the Organisation's single MMX deployment (V1: same-Organisation only). **Every order a TradingClient's PM sends is routed** (a client has only delegated grants and no desk — it cannot place a non-routed order in V1). **Routing is synchronous at intake**: the intake use case validates the order against the client's delegated-grant enabled set, resolves the connected hub and the global account via **GlobalAccountDirectory**, creates the **client-side order** and the **hub-side order**, links them by the deterministic routing id, and flips the client-side order `Received → Routed` — all in the intake transaction, so the hub-side order exists on the hub's desk before intake returns. A grant/enabled-set violation or an unresolved global account → intake rejects (client-side `Rejected` with a reason, no hub-side order created). At a **TradingHub**, intake creates the order on the hub's own desk as today (`Received`, no routing).

**Client-side order** / **Hub-side order**:
A routed order becomes **two linked order records**, correlated by a **routing id**:
- **Client-side order** lives in the TradingClient's LegalEntity scope — keyed by the client PM's `portfolioNumber` and `ExternalOrderReference`; never executed (the client has no desk).
- **Hub-side order** lives in the TradingHub's LegalEntity scope — keyed by the client's **global account** at the hub (substituted portfolioNumber) and a hub-side reference; this is the record the hub desk assigns/executes.
The two are distinct domain objects with different identity and fields; they are never a single shared record. Each LegalEntity scope sees only its own record.

**Routed** (OrderStatus, client-side only):
The client-side order has been handed to the **TradingHub** and a hub-side order exists; awaiting the hub's terminal outcome. A client-side order's lifecycle is `Received → Routed → Executed → Accounted` (terminal alternatives `Rejected` / `Cancelled`) — it never enters `Assigned` (a TradingClient has no desk). `Executed` on the client-side is set by MMX, propagated synchronously from the **hub trader's execution** (see *Routing outcome propagation*), not by the back office. `Accounted` is reached separately on the client-side and hub-side orders (each side has its own contract, booking, and Transactions 2 confirmation). A hub-side order never uses `Routed`; it runs the normal `Received → Assigned → Executed → Accounted` lifecycle.

**Routing outcome propagation**:
The hub's internal `Assigned` state does NOT propagate to the client-side order. When the hub trader executes the hub-side order, MMX **synchronously** sets the linked client-side order to **`Executed`** in the same transaction, copying `ExecutedRate`, `ExecutionTime`, `DealingReference` (and contract-number rules per operation); the client-side counterparty is rendered as the client's own delegated-institution display name ("BNP via LOC"), not the hub's native institution. Other terminal outcomes propagate likewise: hub-side **Rejected** → client-side **Rejected**; hub-side **Cancelled** → client-side **Cancelled**. A **routing failure** (e.g. global-account lookup fails, or the hub refuses the routed order) → client-side order becomes **Rejected** with a routing reason. "Contract validated" is a back-office (Deposits) contract milestone, NOT an MMX order status.

**Accounted on a routed order**:
`Accounted` is set by the back office calling the existing `POST /api/v1/back-office/orders/{orderId}/accounted` callback **twice** — once with the **hub-side orderId** (after the hub's Transactions 2 confirms the hub-side booking), once with the **client-side orderId** (after the client's Transactions 2 confirms the client-side booking). The two accounting flows are independent. `Accounted` removes the order from the PM-facing list of orders not yet in the accounting position.

**Execution event for a routed order** (single event with routing context):
For a routed trade, MMX emits **one** `OrderExecutedV1` from the **hub-side order** (not one per order). It carries the hub-side booking facts (`portfolioNumber` = global account, `counterparty` = the hub's native institution) **plus a routing-context block**: `routingId`, `originatingLegalEntityCode` (the client), `clientOrderId`, `clientPortfolioNumber` (the client PM's portfolio), `clientCounterparty` (the client's "via" display name). The **client-side order's `Executed` transition does NOT emit its own back-office event** — it is MMX-internal (PM-facing status only); this is a deliberate, scoped exception to the "one outbox row per EXECUTED order" rule, applying only to routed client-side orders. The back office creates the hub (LOC) contract from the event, then broadcasts to the client (PAR) back-office instance using the routing context. For native (non-routed) orders the routing context is absent and the existing single-event behaviour is unchanged. Rationale: contract creation and contract reversal/replace then follow the **same** LOC→PAR broadcast pattern.

**Contract reversal / replace** (back-office-internal):
Reversal and replace are back-office **contract**-lifecycle events that originate in the hub (LOC) back office and are broadcast to the client (PAR) back office following the same LOC→PAR pattern as contract creation, correlated by **`routingId`** (captured by the back office on both contracts at creation, from the MMX event). They are **back-office-internal**: MMX order status is NOT affected, `Accounted` remains terminal, and there are no new inbound MMX callbacks for reversal/replace.

**Global account**:
The account reference under which a client **LegalEntity** is known on its hub **LegalEntity's** books — used as the hub-side order's `portfolioNumber` in place of the client's internal portfolio number. The true key is **`(client LegalEntity, client portfolioNumber, hub LegalEntity)`**: one client can hold several accounts at the same hub, discriminated by the originating client portfolio. An account is **multi-currency with a single reference currency** — currency is *not* part of the key (it comes from the order at booking). Resolved at routing time. If it cannot be resolved, routing fails and the client-side order becomes **Rejected** with a routing reason.

> **Known drift (2026-07-20):** the current implementation (`GlobalAccount`, `GlobalAccountDirectory`) still keys on `(client, hub, currency)` — a V1 simplification predating this correction. Unification onto the portfolio key is tracked as a defect (see `.scratch/cross-org-order-routing/issues/09-unify-global-account-keying.md`) and is out of scope for the cross-org routing effort.

**Routing field mapping** (client-side → hub-side order):
- `portfolioNumber` → **global account** for `(client, hub, currency)`.
- Institution/counterparty → the hub's **native institution** (the client's "BNP via LOC" proxy resolves to LOC's own BNP institution).
- `currency`, `amount`, `valueDate`, `OrderType`, `OrderOperation`, `Tenor`/`NoticePeriod`, `MinimumRate` → preserved unchanged. (The tenor/noticePeriod is guaranteed valid at the hub because the client could only enable a subset of what the hub delegated.)
- `ExternalOrderReference` → NOT reused as the hub-side idempotency key. Stored read-only on the hub-side as **`originatingExternalOrderReference`** + **`originatingLegalEntityCode`** for traceability.

**Routing id** (correlation between client-side and hub-side order):
Generated **deterministically from the client-side order id**, so a routing retry resolves to the same hub-side order id and the hub dedupes on it (re-routing the same client-side order MUST NOT create a second hub-side order). The hub-side order's own intake idempotency key is this routing id, not the client's `ExternalOrderReference`.

### Environments and legal entities

**Organisation**:
A bank considered globally (e.g. HSBC, Lombard Odier). Identified by a 4-character **OrganisationCode** (e.g. `LODH`, `HSBC`). Owns one or more **LegalEntities**.
_Avoid_: environment (collides with dev/staging/prod), bank (collides with **Institution**), group, tenant.

**LegalEntity**:
A bank operating in one jurisdiction (e.g. HSBC London, HSBC Paris, Lombard Odier Geneva). Identified by a globally-unique 3-character **LegalEntityCode** (e.g. `LON`, `PAR`, `LOC`), unique across all organisations — no two legal entities share a code even in different organisations. Each LegalEntity belongs to exactly one **Organisation**.
_Avoid_: entity (collides with JPA / DDD entity), branch, office, site.

**MMX deployment boundary**:
MMX is deployed **one instance per Organisation** (e.g. one deployment for `LODH`, a separate one for `HSBC`), for fault isolation between organisations. All LegalEntities of an Organisation share that single deployment. A user connects to a specific LegalEntity within the deployment and sees only that LegalEntity's settings. Order routing between LegalEntities of the same Organisation is therefore in-process; there is no cross-organisation routing in V1.

**TradingHub**:
A **LegalEntity** that operates its own trading desk and executes orders — its own, and those routed to it by its **TradingClients**. Exposes the Desk menu to its users. A TradingHub is the originator of institution onboarding delegations to its clients.

**TradingClient**:
A **LegalEntity** without its own trading desk. Its orders MUST be routed to its connected TradingHub for execution. In V1 a TradingClient is connected to exactly one TradingHub within the same Organisation. Its users see Settings only — never the Desk. A LegalEntity is either a TradingHub or a TradingClient, never both (V1).
_Avoid_: client (alone — overloaded with API clients), hub (alone — use TradingHub).

**Role and connection mutability**:
A LegalEntity's TradingHub/TradingClient role is configurable reference data. In V1 the hub connection of a TradingClient may be reassigned to another TradingHub in the same Organisation; flipping a hub into a client (or vice versa) is deferred to V2. Changes do not retroactively rewrite already-routed or executed orders — historical orders keep the routing decision in effect when they were routed.

### Products

**ProductType**:
What kind of money market product an order is. V1 values: **Fiduciary** | **Deposits**. V1 implements **Fiduciary only**; Deposits is deferred and recorded here as a scope boundary, not a silent omission.

**Fiduciary**:
Product where the client signs a contract with an *external* institution (e.g. BNP). The counterparty is an onboarded **Institution**. This is the existing product. Fiduciary institutions on a **TradingClient** are obtained via a **delegated institution grant** from its **TradingHub** (see *Reference data*), never native onboarding.

**Deposits**:
Product where the **LegalEntity** keeps the money on its *own* balance sheet; the counterparty is the LegalEntity itself. A **TradingClient** may onboard its own LegalEntity as a Deposits counterparty. Deferred — not implemented in V1.

### Reference data

**Managed currency**:
Catalog entry controlling which currencies and tenors/notice periods are valid for intake and trader updates. **Owned by the TradingHub**; a TradingClient has no currency catalog of its own and obtains currencies read-only from its connected TradingHub (an in-process read within the shared deployment, resolving the client's hub connection).

**Institution**:
Onboarded financial institution (code, display name, suffix rules) usable as a **Fiduciary** counterparty. At execute, trader selects **institutionCode**; response **counterparty** is derived from institution display name (not free text). A TradingHub onboards institutions natively; a TradingClient obtains Fiduciary institutions only via a **delegated institution grant** from its hub.
_Avoid_: counterparty (for execute request body — legacy free-text is removed).

**Delegated institution grant** (Fiduciary, TradingClient side):
A TradingHub grants a TradingClient the right to use one of its onboarded institutions. The grant is keyed by **`(institution, client LegalEntity, currency)`** and specifies the set of **Tenors** / **NoticePeriods** the client may activate. The client does NOT create an independent institution row; it holds a thin **proxy** referencing the hub's institution, with a **derived display name** `"{hub institution display name} via {hub LegalEntityCode}"` (e.g. `BNP via LOC`). The client may enable all or a subset of the granted tenors/notice periods per `(institution, currency)`. Intake at the client validates the order's `(institution, currency, tenor|noticePeriod)` against the client's enabled set. Routing of an order placed on a delegated institution is implicit — it always routes to the granting TradingHub. In V1 a TradingClient holds only delegated Fiduciary grants (no native Fiduciary onboarding).

**Grant vs hub own-intake enablement**:
The client grant set is **independent** of the hub's own intake enablement (**Managed currency** / institution settings): the hub may enable a tenor/notice period for a client that it does NOT enable for its own PM. The hub's own intake enablement gates only what the hub's PM may place via intake; it does not bound the grant. The hub-side order arrives via routing (not via the hub's intake), so the hub's intake enablement does not apply to it — the **grant itself is the authority** for the routed tenor, and the hub desk executes it. The only constraint on the grant is that the institution is onboarded (active) at the hub.

**Grant lifecycle (prospective only)**:
A hub may revoke, reduce, expand, or fully revoke a grant at any time; all changes are **prospective** — they block new intake/routing for the affected `(institution, client, currency, tenor|noticePeriod)` but do NOT retroactively cancel client-side orders already in `Received` or `Routed`, and do NOT touch hub-side orders already on the desk. In-flight and historical orders keep the rights in effect when they were placed. Deactivating the hub's underlying institution blocks new intake for all clients (and the hub's own new orders) for that institution; in-flight orders still complete. To stop an in-flight order, the hub must cancel the hub-side order explicitly (which propagates `Cancelled` to the client-side order).

**Term rate**:
Daily uploaded rate row keyed by institution, currency, tenor, and trading date. **Managed by the TradingHub** (the desk that deals); a TradingClient has read-only visibility of term rates for its delegated institutions and never uploads/manages them.

**OnCall rate curve point**:
Identified by **(institution, currency, noticePeriod)**. Holds ordered **rate segments**. **Managed by the TradingHub**; a TradingClient has read-only visibility for its delegated institutions.

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
External source of orders via REST intake; only source in V1. **Scoped per Organisation** — one PM serves all the LegalEntities of the organisation, so the order's **`legalEntityCode`** is a required field in the intake request; PM is authenticated at the organisation level and the `legalEntityCode` must identify a LegalEntity of that organisation. (Contrast with trader/settings endpoints, which derive the LegalEntity from the **MMXUser session scope** — no body field.)

**Back office** (legacy application **"Deposits"**, still in use):
Downstream consumer of execution and on-call rate handoff messages (outbox / callbacks); confirms accounting and rate segments. In the clean target it consumes MMX's `OrderExecutedV1` events and calls the `accounted` callback; it **never reads or writes the MMX order DB** (replacing the legacy FiduTrader DB polling/cross-DB writes). It owns contracts, fees/taxes, booking to **Transactions 2**, and the LOC→PAR contract broadcast (creation, reversal, replace) correlated by **`routingId`**. Distinct from the **Deposits** ProductType and the **PositionApi** port below.

**Transactions 2**:
External booking system. The back office sends booking transactions after contract validation; Transactions 2 confirms them, which the back office relays to MMX via the `accounted` callback → order becomes `Accounted` (removed from the PM-facing not-yet-accounted list).

**FiduTrader**:
Legacy two-tier predecessor of MMX (UI + database, no backend). Order state lived in its DB, which the Deposits back office polled/wrote directly. MMX replaces FiduTrader and takes ownership of order state, routing, and the `Executed`/`Accounted` transitions.

**PositionApi** (was `Deposits` / `PositionApi`):
Modeled port for contract balance checks (e.g. Decrease validation); not full product scope in V1. Renamed so that bare **"Deposits"** in the glossary refers only to the **ProductType**; the back-office application is always qualified ("Deposits back-office application" / "Deposits BO").

**GlobalAccountDirectory**:
Integration port that resolves the **global account** reference for a `(client LegalEntity, hub LegalEntity, currency)` triple at order-routing time. In V1 it is backed by MMX-managed reference data (the port is the seam; a future implementation may point at an external account-management system).

## Relationships

- An **Organisation** owns one or more **LegalEntities**; each **LegalEntity** belongs to exactly one **Organisation**.
- MMX is deployed one instance per **Organisation**; all its **LegalEntities** share that deployment. Order routing between LegalEntities of the same Organisation is in-process; no cross-organisation routing in V1.
- A **LegalEntity** is either a **TradingHub** or a **TradingClient**, never both (V1). A **TradingClient** is connected to exactly one **TradingHub** within the same Organisation (V1).
- An **MMXUser** holds N allowed `(LegalEntity, role)` scopes; a session is bound to one active scope, switchable by re-scope; the server never accepts a free per-request entity choice.
- A **routed order** is two linked records — a **client-side order** (TradingClient scope) and a **hub-side order** (TradingHub scope) — correlated by a deterministic **routing id**; never one shared record.
- A **delegated institution grant** links a hub's onboarded **Institution** to a **TradingClient** for a specific `currency`, with a granted set of **Tenor**/**NoticePeriod**; the client's display name is derived ("`{name} via {hubCode}`").
- **Managed currency**, **Term rate**, and **OnCall rate curve** are owned/managed by the **TradingHub**; a **TradingClient** reads them from its connected hub (currencies) or has read-only visibility (rates for granted institutions).
- The back office (Deposits application) consumes MMX's `OrderExecutedV1` and calls the `accounted` callback; it never reads or writes the MMX order DB. Contract creation, reversal, and replace are broadcast hub→client within the back office, correlated by **`routingId`**.
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

- Core order lifecycle (intake, assign, execute, cancel/reject): [openspec/specs/money-market-order-lifecycle/spec.md](openspec/specs/money-market-order-lifecycle/spec.md)
- Desk queues and accounting: [openspec/specs/desk-order-queries/spec.md](openspec/specs/desk-order-queries/spec.md), [openspec/specs/trader-executed-queue/spec.md](openspec/specs/trader-executed-queue/spec.md), [openspec/specs/back-office-accounting-handoff/spec.md](openspec/specs/back-office-accounting-handoff/spec.md)
- HTTP contracts: [contracts/001-mm-order-processing/api-v1.md](contracts/001-mm-order-processing/api-v1.md), [contracts/002-trader-orders-views/api-v1.md](contracts/002-trader-orders-views/api-v1.md)
- On-call curve rules: [openspec/specs/oncall-rate-curve-management/spec.md](openspec/specs/oncall-rate-curve-management/spec.md)
- ADRs (when present): [docs/adr/](docs/adr/)
