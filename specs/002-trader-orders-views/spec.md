# Feature Specification: Trader order views and accounting handoff

**Feature Branch**: `002-trader-orders-views`  
**Created**: 2026-05-09  
**Status**: Draft  
**Input**: User description: Orders View — assigned list visibility vs execution; Received default horizon and show-all; Accounted status after portfolio impact; executed-not-yet-accounted view with counterparty; back-office notified on execution and confirms accounting; Term vs OnCall tabs with Received / Assigned / Executed sub-tabs; tenor and notice period visible everywhere.

## Clarifications

### Session 2026-05-09

- Q: How does the Term vs OnCall workspace choice behave across browser sessions / logins? → A: Fixed default **OnCall** each session; **no** cross-session persistence of last workspace (Option D).
- Q: Navigation chrome on Assigned / Executed? → A: Workspace + sub-view (Received / Assigned / Executed) MUST stay **visually indicated** as active on every desk queue screen so traders are never ambiguous about Term vs OnCall or which tab they chose.
- Q: Order details vs primary navigation / back? → A: When the trader opens **order details** from a queue list (**View**), the **same** workspace (**Term** vs **OnCall**) and **same** sub-view (**Received** / **Assigned** / **Executed**) MUST remain **visually indicated** as active in the primary navigation. The control that returns to the queue (**Back to …**) MUST navigate to **that** queue, not a fixed default. Opening order details **without** originating queue context (e.g. pasted URL) is allowed to show **no** queue-specific selection until the user navigates; the back target MAY fall back to the default desk entry (**OnCall** > **Received**).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Term vs OnCall workspace structure (Priority: P1)

Traders work Money Market orders in two parallel workspaces: **Term** and **OnCall**. Within each workspace, the same three operational views exist — **Received**, **Assigned**, and **Executed**. Switching between Term and OnCall changes which orders appear everywhere; **Term** and **OnCall** orders MUST NOT appear mixed on the same focused workspace surface. Each **new authenticated session** opens on **OnCall** by default; workspace choice within a session is **not** persisted across logout or session boundary — the next session **again** defaults to **OnCall**.

The trader application MUST expose **persistent primary navigation** with two tiers: (**1**) workspace selection (**Term** vs **OnCall**) and (**2**) sub-view selection (**Received**, **Assigned**, **Executed**). On every desk queue screen (Received, Assigned, Executed for the active workspace), **both** the active workspace **and** the active sub-view MUST be **visually distinguished** (e.g. highlighted or equivalent) so the trader always knows where they are. This MUST hold when navigating directly to Assigned or Executed, not only from Received (no “lost” workspace context on secondary views).

**Why this priority**: Navigation and mental model for everything else; without a clear split, horizon rules, assignment visibility, and executed queues cannot be interpreted correctly.

**Independent Test**: Start a **new session** — confirm the active workspace is **OnCall** and only OnCall orders appear under Received, Assigned, and Executed until the trader switches. Select **Term** — confirm only Term orders appear under those three views for that session path; switch back to **OnCall** — confirm only OnCall orders appear.

**Acceptance Scenarios**:

1. **Given** a **new authenticated session** (or default entry before any workspace switch), **When** the trader opens Received, Assigned, or Executed, **Then** the active workspace is **OnCall** and every listed order is an OnCall order (no Term rows).
2. **Given** the trader has switched to the **Term** workspace, **When** they open Received, Assigned, or Executed there, **Then** every listed order is a Term order (no OnCall rows).
3. **Given** the trader has switched to **OnCall** (after Term or from default), **When** they open Received, Assigned, or Executed there, **Then** every listed order is an OnCall order (no Term rows).
4. **Given** the trader moves from one workspace to the other within the same session, **When** they return to the first workspace, **Then** lists reflect only that order type again without stale cross-type rows.
5. **Given** the trader used **Term** in a prior session, **When** they start a **new** session, **Then** the desk opens on **OnCall** (workspace choice from the prior session is not restored).
6. **Given** the trader is viewing **Assigned** or **Executed** inside a workspace (**Term** or **OnCall**), **When** they look at the primary navigation chrome, **Then** **that workspace** appears as the selected workspace **and** **Assigned** or **Executed** appears as the selected sub-view (not Received).
7. **Given** the trader opened **order details** from a queue (e.g. **Term** > **Assigned** via **View**), **When** they are on the details screen, **Then** the primary navigation still shows **that** workspace and **that** sub-view as active **and** the back control returns to **that** queue (not another sub-view or workspace).

---

### User Story 2 — Assigned: shared visibility, guarded execution (Priority: P2)

On **Assigned** (within the active Term or OnCall workspace), **every** trader sees **all** orders currently in assigned status for that type. Any trader may open order details. Only the trader **currently assigned** to an order may **execute** it (record execution); other traders cannot successfully complete execution.

**Why this priority**: Matches desk transparency with execution accountability.

**Independent Test**: Two traders open the same assigned list — both see the same order after assignment; non-assignee execution attempt fails; assignee succeeds.

**Acceptance Scenarios**:

1. **Given** an order is assigned in the active workspace type, **When** any trader opens the Assigned view, **Then** that order appears in the list for everyone entitled to use the desk application.
2. **Given** an order is assigned to Trader A, **When** Trader B opens details from Assigned, **Then** Trader B can read information consistent with product visibility rules, **and** cannot successfully execute the order (blocked or clearly rejected).
3. **Given** an order is assigned to Trader A, **When** Trader A executes per product rules, **Then** execution completes and the order leaves Assigned on the intended path.

---

### User Story 3 — Received: near-term default and optional full list (Priority: P3)

On **Received** (within the active workspace), traders **by default** see only orders whose relevant scheduling date falls within a **near-term window** (see Assumptions). A clear control lets the trader **include all** Received orders regardless of that window. Expansion is **session-scoped**: after logout or session end, the next session returns to the **default** narrow view unless the trader enables full list again.

**Why this priority**: Reduces noise for daily triage while preserving access to the full pipeline.

**Independent Test**: Seed orders inside and outside the default window — default list excludes far-dated rows; enabling full list shows them; disabling restores default; **new session** resets to default narrow view.

**Acceptance Scenarios**:

1. **Given** Received holds orders inside and outside the default window, **When** the trader opens Received with defaults, **Then** only in-window orders appear.
2. **Given** default filtering is on, **When** the trader activates “show all” (or equivalent), **Then** all Received orders for that workspace type appear.
3. **Given** “show all” is active, **When** the trader turns it off, **Then** the list returns to default window behaviour.
4. **Given** “show all” was used this session, **When** the authenticated session ends, **Then** the next session uses the default Received window until expanded again.

---

### User Story 4 — Executed not yet accounted: visibility, handoff, and confirmation (Priority: P4)

Within the active workspace, **Executed** lists orders that have been **executed in the trading application** but are **not yet accounted** (portfolio position not yet confirmed updated). **Counterparty** from execution is visible. Executed outcomes are **sent toward** the **back-office** process so bookkeeping can run; the trading application **accepts confirmation** when an execution is **accounted**, after which the order is treated as **Accounted** for trader-facing purposes (see User Story 5).

Failures or delays in transmitting the execution outcome to back-office MUST be distinguishable on this surface from the case where transmission succeeded and the desk is **only waiting for accounting** (see Edge Cases).

**Why this priority**: Connects trading completion to books and gives traders an honest pending-accounting picture.

**Independent Test**: After execution, order appears on Executed-not-accounted with counterparty visible; when accounting is confirmed, order leaves that cohort; simulate or observe transmit fault — row shows a **distinct** integration/handoff signal vs normal pending-accounted presentation.

**Acceptance Scenarios**:

1. **Given** an order is Executed but not Accounted, **When** the trader opens Executed for that workspace, **Then** the order appears **and** execution counterparty is visible.
2. **Given** an execution reaches Executed, **When** the product’s handoff policy applies, **Then** the executed outcome is communicated toward back-office so accounting can complete without the trader re-entering the same facts.
3. **Given** back-office confirms accounting for that execution, **When** the trading application processes that confirmation, **Then** the order becomes Accounted and no longer appears among executed-not-accounted items.

---

### User Story 5 — Accounted lifecycle semantics (Priority: P5)

**Accounted** means the execution is booked and the portfolio position reflects it per back-office confirmation. Such orders **leave** trader **working** queues for this feature (Received / Assigned / Executed-not-accounted). This baseline does **not** introduce a dedicated trader screen whose **primary purpose** is browsing **Accounted-only** history for desk work.

**Why this priority**: Avoids duplicate queues and keeps trader attention on actionable work.

**Independent Test**: Confirm one order through accounting — it disappears from executed-not-accounted and does not reappear as pending accounting; no mandatory Accounted-only list is required for acceptance.

**Acceptance Scenarios**:

1. **Given** accounting is confirmed for an execution, **When** the trader opens Executed-not-accounted, **Then** that order no longer appears there.
2. **Given** a trader session, **When** they navigate the three sub-views for the focused type, **Then** there is **no separate mandatory workspace** whose sole purpose is listing Accounted-only orders for operational browsing.

---

### Edge Cases

- **Assigned visibility vs execution**: Reassignment changes who may execute; list visibility remains desk-wide.
- **Duplicate or retried accounting confirmations**: Confirmation handling MUST be **idempotent** (single Accounted outcome, no duplicate side effects).
- **Horizon boundaries**: Near midnight or timezone boundaries — scheduling dates interpreted in the **same business timezone** as the existing Money Market product (single coherent definition in planning).
- **Handoff fault vs books delay**: Transmit failure or undelivered payload MUST NOT use the same trader-facing meaning as “handed off, awaiting accounting.”
- **Booking cannot proceed** without Accounted: Order stays Executed-not-accounted with **ordinary** pending-accounting presentation for staleness (no special trader “remediation” queue singled out by this baseline — escalation via ops/support outside this spec).
- **Switch workspace while “show all” is on**: Filtering reapplies to the **new** workspace’s dataset; session toggle behaviour unchanged.
- **New session vs last workspace**: After logout or session end, the next session MUST **default to OnCall** even if the trader had **Term** active when the prior session ended.

### REST contract acceptance (**executed-orders-accounting**)

The following behaviours are exercised through the canonical HTTP contract in `contracts/openapi.yaml` / `contracts/api-v1.md`:

- **`GET …/orders/term/executed`** and **`GET …/orders/oncall/executed`** return paged **`OrderSummaryResponse`** rows scoped to workspace type (`TERM` vs `ON_CALL`) and **`EXECUTED`** status only; **`ACCOUNTED`** orders do **not** appear. Each executed row exposes **counterparty** (omit-null when absent).
- **`POST …/back-office/orders/{orderId}/accounted`** (no Trader header baseline) acknowledges portfolio accounting **idempotently** (`EXECUTED` → `ACCOUNTED`; **`409`** if the order is not in **`EXECUTED`**; **`200`** replay when already **`ACCOUNTED`**). **`ORDER_NOT_FOUND`** is **`404`**.

Detailed user narratives remain in User Stories 4–5 and FR-008–FR-012 below.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The trader application MUST present Money Market order work in **two top-level workspaces**: **Term** and **OnCall**. Each workspace MUST offer **Received**, **Assigned**, and **Executed** sub-views. Lists MUST NOT mix Term and OnCall orders on the same workspace surface. On **session start** (after authentication for that session), the **default active workspace MUST be OnCall** until the trader switches to Term; **last workspace MUST NOT be persisted** across sessions or logins — each new session MUST start on **OnCall** again. The **workspace selector** MUST reflect the active workspace on **all** desk queue screens (**Received**, **Assigned**, **Executed**). The **sub-view selector** MUST reflect the active sub-view on those same screens; navigation MUST NOT leave ambiguous state where Assigned or Executed appears active but the workspace appears inactive or vice versa (except where other screens omit the chrome by design — e.g. order detail may omit sub-view highlighting if clearly out-of-queue scope).
- **FR-002**: On **Assigned**, all entitled traders MUST see **all** assigned orders for the active workspace type. Any trader MUST be able to open order details subject to existing visibility rules.
- **FR-003**: Only the trader **currently assigned** to an order MUST be able to **execute** it from Assigned; others MUST receive a clear, safe failure (no silent success).
- **FR-004**: On **Received** with **default** settings, the system MUST restrict rows to orders whose scheduling date satisfies the **default near-term window** (see Assumptions). The trader MUST have an explicit control to **show all** Received orders for that workspace type within the session.
- **FR-005**: **Show all** on Received MUST be **session-scoped** only — not persisted as a lasting preference across logout or session boundary (see Assumptions).
- **FR-006**: For **Term** rows on Received, Assigned, and Executed primary list/detail surfaces in scope, **Tenor** MUST be visible or reachable in one obvious step without leaving context.
- **FR-007**: For **OnCall** rows on Received, Assigned, and Executed primary list/detail surfaces in scope, **Notice period** MUST be visible or reachable in one obvious step without leaving context.
- **FR-008**: The system MUST recognise lifecycle **Accounted**: execution is booked and portfolio impact is acknowledged via **back-office confirmation** used as the authoritative signal.
- **FR-009**: **Executed** (within workspace) MUST list **Executed** orders that are **not** **Accounted**, and MUST show execution **counterparty** on that view.
- **FR-010**: After execution, the trading application MUST communicate the executed outcome toward **back-office** so accounting can proceed **without** traders re-entering the same execution facts for that purpose; retry/recovery MUST NOT require re-recording execution.
- **FR-011**: The trading application MUST accept **accounting confirmation** from the back-office path and MUST transition the order to **Accounted** when appropriate; negative booking outcomes MUST NOT flip an order to Accounted.
- **FR-012**: Orders that become **Accounted** MUST NOT appear in the executed-not-accounted trader cohort.
- **FR-013**: While transmit of execution outcome has **not** succeeded per product rules, the **Executed** view MUST surface a **distinct** trader-visible state from “submitted toward back-office; awaiting accounting” (integration/handoff vs books delay).
- **FR-014**: For executed-not-accounted rows where handoff has **succeeded**, the **Executed** view MUST convey **how long** execution has awaited accounting (staleness), using one authoritative business timestamp per order; **longer-waiting** items MUST be **more salient** than fresher ones per desk-agreed rules; **default list order** MUST favour **longest-waiting first** unless the trader chooses another sort.
- **FR-015**: The trader application MUST NOT offer a primary browsing experience whose **sole purpose** is listing **Accounted-only** orders for desk operations.

### Key Entities *(include if feature involves data)*

- **Money Market order (extended concept)**: Existing order; extended with recognition of **Accounted** vs **Executed-only**, and presentation fields for lists (Tenor / Notice period, counterparty on executed-not-accounted).
- **Assignment**: Determines execute permission; unchanged rule — only assignee executes.
- **Execution record**: Counterparty and execution timestamp anchor staleness and display.
- **Accounting confirmation**: Authoritative signal that portfolio position reflects the execution → **Accounted**.
- **Handoff / transmit state** (conceptual): Whether execution outcome was accepted on the path to booking — distinct from **Accounted**.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In validation, ≥ **90%** of desk users correctly distinguish “executed only” vs “accounted” on sample scenarios after a short walkthrough.
- **SC-002**: Two traders viewing the same Assigned list both see the same assigned order **within one refresh cycle** of its appearance; non-assignee cannot complete execution (**100%** enforcement in scripted tests).
- **SC-003**: For a labelled sample of Received orders spanning in-window and out-of-window scheduling dates, **100%** of **include/exclude** decisions under the **default** filter match the stated window rule in Assumptions.
- **SC-004**: **100%** of valid accounting confirmations remove the order from the executed-not-accounted view within the product’s normal processing window (target latency defined in planning).
- **SC-005**: With Term vs OnCall workspaces exercised separately, **zero** cross-type orders appear on the wrong workspace in acceptance testing.
- **SC-007**: In scripted UX checks covering Received, Assigned, and Executed inside each workspace, **100%** of steps confirm primary navigation shows the **correct active workspace** and **correct active sub-view** (no mismatched highlighting between workspace and queue tab).
- **SC-006**: In scenarios with at least three executed-not-accounted orders of different ages, ≥ **90%** of participants identify the **longest-waiting** order using only the Executed view.

## Assumptions

- Builds on **Money Market Order Processing** (`001-mm-order-processing`) for intake, assignment, execution capture, and baseline statuses.
- **Default Received window**: “At most two days in the future” is implemented as an **inclusive** span of **three calendar dates** — **today** and the **next two calendar days** — measured against the **same scheduling date field** the product already uses for Received lists (exact field name is an implementation detail aligned with **001**).
- **Show all** is intentionally **not** persisted across sessions so each session starts on the **near-term funnel** unless the trader opts in again.
- **Workspace (Term vs OnCall)** is **not** persisted across sessions: each new session **defaults to OnCall**; switching to Term applies **only within that session**.
- Market dealing outside the app; **execution** means recording outcome in-app.
- **Back-office** exists; wire format, authentication, and retry policy for handoff and callbacks are specified in planning and contracts, not in this document.
- Calendar days unless desk policy later mandates business days.
