## Why

Once a Trader executes a Money Market order, the order disappears from every screen even though the deal is not yet booked downstream: the back-office application still has to confirm that the execution was settled and impacted the portfolio position. Today, mmx treats `EXECUTED` as terminal, has no record of when an execution actually completes, and provides Traders no operational view of "executions in flight". Adding an `ACCOUNTED` terminal state — driven by a back-office callback — closes the lifecycle, and the new Executed screen gives Traders the counterparty information they need for follow-up and dispute tracking while an execution is pending accounting.

## What Changes

- Introduce a new terminal `OrderStatus.ACCOUNTED` and demote `EXECUTED` from terminal to a transient state. The only allowed transition out of `EXECUTED` is `EXECUTED → ACCOUNTED`.
- Add a Trader screen listing orders currently in `EXECUTED` (not yet accounted), one variant per workspace (**Term**, **OnCall**), with the execution **counterparty** visible on each row.
- **Activate the outbound back-office handoff**: when an order transitions into `EXECUTED`, mmx makes a **best-effort synchronous** call to the back-office application via the existing outbound port (today a no-op and **never invoked** by `ExecuteOrderService` — the skeleton was scaffolded in feature 001, this change wires it in **after the `EXECUTED` transactional commit**). Reliable delivery via transactional outbox is deferred to a future change.
- **Rename** the existing outbound port `DepositsGateway` → `BackOfficeGateway` (and the matching adapter `NoOpDepositsGateway` → `NoOpBackOfficeGateway`) so the ubiquitous language matches the **back-office** system the port has always represented. Signature is unchanged.
- Add an inbound integration: when the back-office signals that a transmitted execution has been accounted, mmx transitions the order to `ACCOUNTED` and the row leaves the Executed list.
- Extend the canonical product API contract-first: update `specs/002-trader-orders-views/contracts/openapi.yaml` and its `api-v1.md` mirror with the Executed list endpoint(s) and the back-office accounting callback endpoint, then regenerate server stubs before implementing controllers.
- Treat all behavioural work (status transition, application services, REST endpoints, gateway adapter) as **TDD-first** per the project default — failing tests at the narrowest layer that can fail for the wrong reason, then minimal implementation.

No breaking changes for existing HTTP endpoints. The order-status enum gains a value (`ACCOUNTED`); serialized payloads and consumers that round-trip status SHALL accept it. The Java rename `DepositsGateway → BackOfficeGateway` is internal to the codebase and does not cross any HTTP wire contract.

**Out of scope** for this change (deferred):

- The broader Term / OnCall **tab + Received / Assigned / Executed sub-tab** restructuring of the workspace UI mentioned in `scratch.txt` — only the Executed view itself is in scope here; integrating it into a sub-tab layout is a separate change.
- Any persistent, multi-session Trader preference for the Executed view filters; this change introduces only the default list behaviour.
- **Reliable outbound transmission to the back-office.** This change uses a best-effort **synchronous** call to the back-office gateway after the `EXECUTED` transaction commits. If the back-office is unreachable the transmission is logged and skipped; no automatic retry, no durable queue. Replacing this with a **transactional outbox** (and a drainer) is deferred to a separate change.
- **`contractNumber` generation per `OrderOperation`.** Today `ExecuteOrderService.execute(...)` mints a fresh `contractNumber` on every call regardless of operation type (SUBSCRIPTION / INCREASE / DECREASE / REDEMPTION). The intended invariant is that `contractNumber` is generated only on **SUBSCRIPTION** and inherited by subsequent operations on the same subscription. This is a known bug; it is **out of scope** for this change and will be addressed separately.

## Capabilities

### New Capabilities

- `trader-executed-queue`: per-workspace (**Term** / **OnCall**) Trader list of orders currently in `EXECUTED`, surfacing the execution **counterparty**, with rows leaving the list as soon as the order transitions to `ACCOUNTED`.
- `back-office-accounting-handoff`: bidirectional integration with the back-office application — outbound transmission of every newly executed order, and inbound notification that flips the order's status from `EXECUTED` to `ACCOUNTED`. Owns the `EXECUTED → ACCOUNTED` lifecycle rule (only this capability may drive that transition).

### Modified Capabilities

_None._ The existing `trader-received-queue` capability is unaffected (it filters on `RECEIVED`). The order-status lifecycle is not yet owned by any existing capability spec, so the new `EXECUTED → ACCOUNTED` transition is introduced as part of `back-office-accounting-handoff` rather than as a delta on an existing spec.

## Impact

**Domain — `mmx-domain`**

- `OrderStatus`: add `ACCOUNTED`; revise `ALLOWED_TRANSITIONS` so `EXECUTED → {ACCOUNTED}` is permitted and `ACCOUNTED` is terminal.
- `MoneyMarketOrder`: new behaviour for the `EXECUTED → ACCOUNTED` transition (e.g. `markAccounted(...)`), enforcing the invariant that only an order in `EXECUTED` can be accounted.

**Application — `mmx-application`**

- Promote the existing orphan query methods `OrderQueryService.listExecutedTermOrders(...)` / `listExecutedOnCallOrders(...)` to first-class `port/in` use cases (`ListExecutedTermOrdersUseCase`, `ListExecutedOnCallOrdersUseCase`), mirroring the existing Received / Assigned use-case pattern. The query logic is already implemented; this change adds the interfaces and the REST wiring.
- New `port/in`: a use case to record an "accounted" notification from the back-office (`MarkOrderAccountedUseCase` or equivalent), driven by the inbound callback endpoint.
- **Rename** existing `port/out` `DepositsGateway` → `BackOfficeGateway`. Signature is unchanged: `void notifyExecution(MoneyMarketOrder order)`. Update all imports across `mmx-application` / `mmx-adapter-out-integration` / `mmx-bootstrap` and any tests that mock or stub it.
- Wire `backOfficeGateway.notifyExecution(saved)` so the call fires **after** the `@Transactional` `execute()` method commits. The recommended placement is the orchestrating caller (controller or facade), not inside the transactional boundary; this preserves the invariant that the back-office can never observe an order before mmx has persisted `EXECUTED`. The call is best-effort — a failure is logged and swallowed (see "Out of scope: reliable outbound transmission").

**Inbound REST — `mmx-adapter-in-rest` (contract-first)**

- Extend `specs/002-trader-orders-views/contracts/openapi.yaml` and `specs/002-trader-orders-views/contracts/api-v1.md` with:
  - Trader-facing endpoint(s) returning the Executed list per workspace, including the counterparty.
  - A back-office callback endpoint that delivers the "accounted" notification.
- Regenerate stubs, then implement controllers against the generated interfaces. Authentication boundary for the back-office callback (vs the Trader product API) is settled in `design.md`.

**Outbound integration — `mmx-adapter-out-integration` (existing module, reused)**

- Reuse the existing `mmx-adapter-out-integration` module that already houses the no-op gateway. Rename `NoOpDepositsGateway` → `NoOpBackOfficeGateway`, update the bean factory in `OrderModuleConfiguration` (`depositsGateway()` → `backOfficeGateway()`, return type, import), and update the module description in `backend/mmx-adapter-out-integration/pom.xml`.
- Transport choice (HTTP, messaging, in-memory stub) is a design decision; for the POC a stub adapter that records transmissions in-memory is acceptable as long as the port contract is honoured. Whether the stub stays the only implementation or sits alongside a real one is settled in `design.md`.

**Persistence — `mmx-adapter-out-persistence`**

- Map the new `ACCOUNTED` status in JPA. A Flyway migration is needed only if the persisted status column has a CHECK constraint enumerating allowed values; otherwise the change is enum-only.

**Frontend — `frontend/`**

- New feature module(s) for the Executed view, one variant per workspace (Term / OnCall), aligned with existing standalone-component patterns and the generated API client.
- Surface the counterparty as a visible column.
- Vitest unit tests for the new components/services; Cypress e2e covering at least the Term Executed view (load → accounted callback → row removal).

**Specs (SDD)**

- Each new capability listed above creates a new `openspec/specs/<capability>/spec.md` file in the specs phase.
- The product API change must remain aligned with `specs/002-trader-orders-views/spec.md` and contracts in the same delivery (per project Principle VI).
- The `DepositsGateway → BackOfficeGateway` rename touches feature 001 SDD artifacts. In the same delivery: update descriptive references in `specs/001-mm-order-processing/plan.md` (lines 142, 189, 889-890, 938) to the new name. **Do not rewrite** the historical `[x]` task entries in `specs/001-mm-order-processing/tasks.md` (lines 68, 86) — they record what feature 001 actually delivered. A short footnote in `tasks.md` noting the rename was delivered under change `executed-orders-accounting` is sufficient.
