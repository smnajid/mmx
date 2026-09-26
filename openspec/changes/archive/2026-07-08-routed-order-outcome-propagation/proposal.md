## Why

Hub→client terminal-outcome propagation for routed orders is smeared across two application services (`ExecuteOrderService.propagateHubOutcome` / `OrderLifecycleService.propagateHubCancelOrReject`) with the same guard, the same client lookup, and the same apply-outcome shape — but asymmetric failure semantics and **zero unit tests on the cancel/reject path**. The current code also has two real gaps against ADR-0002 ("status must be synchronised; terminal outcomes propagate"): (1) hub cancel/reject on a broken pair (missing client-side order) **silently no-ops**, leaving the client-side order stuck `ROUTED` while the hub is terminal; (2) cancel/reject run with **no transaction boundary**, so hub and client saves can commit independently and desync on a failed client save. Hub execute additionally fetches the client-side order **twice** in one request. Consolidating propagation behind one port closes both gaps, gives the untested cancel/reject path a single testable seam, and eliminates the double-fetch.

## What Changes

- **New application-internal port** `RoutedOrderOutcomePropagation` (+ `RoutedOrderOutcomePropagationService` impl) in `mmx-application`, owning "apply the linked client-side order's terminal outcome when a hub-side order reaches execute / cancel / reject, and (for execute) produce the `ExecutionHandoffRoutingContext` for the outbox." Both `ExecuteOrderService` and `OrderLifecycleService` inject it and become thin callers.
- **New domain exception** `RoutedOrderPairIntegrityException` in `mmx-domain`, thrown on all three outcomes when the linked client-side order is missing (a corrupted pair — an ADR-0002 invariant violation). Replaces the asymmetric current behavior: execute threw `InvalidOrderException`; cancel/reject silently no-op'd.
- **New `TransactionalOrderLifecycleUseCase`** wrapper in `mmx-bootstrap` (one class covering both `CancelOrderUseCase` and `RejectOrderUseCase`), mirroring the existing `TransactionalExecuteOrderUseCase`. Hub-save + client-propagation become atomic on cancel/reject, matching execute's existing atomicity.
- **Hub execute no longer fetches the client-side order twice.** `propagateExecution` returns `ExecutionPropagationResult(clientOrder, handoffContext)`; the outbox context is a byproduct of propagation. `ExecuteOrderService.routingContextFor` is deleted.
- **Behavior change (BREAKING for the corrupt-pair edge case):** hub cancel/reject on a routed order whose linked client-side order is missing now **throws `RoutedOrderPairIntegrityException`** instead of silently completing. This only triggers in a state that already violates ADR-0002; surfacing it is the fix, not a regression. The trader-facing REST surface (paths, schemas, status codes) is **unchanged** — no contract edit.
- TDD: lock current behavior red→green first, extract the port behavior-preserving, flip the error policy red→green, add the tx wrapper — per the migration plan in `design.md`.

No new product REST surface. No frontend change. No DB/Flyway change. Routing correlation model (`routingId` + `isHubSideRoutedLink` sentinel) and the `OrderRepository` fat port are **unchanged** (deferred to candidates #3 and #8).

## Capabilities

### New Capabilities

_None._ The propagation module is an application-internal seam, not a user-facing capability.

### Modified Capabilities

- `order-routing`: the "Routing outcome propagation to the client-side order" requirement gains an explicit broken-pair invariant (a missing linked client-side order is an error, not a silent skip) and an atomicity requirement (hub terminal transition and the propagated client transition commit in one transaction). Today's spec mandates propagation but is silent on the broken-pair and transaction-boundary cases; this change makes both explicit and testable.

## Impact

- **Code:**
  - `mmx-application`: new `RoutedOrderOutcomePropagation` port + `RoutedOrderOutcomePropagationService` impl + `ExecutionPropagationResult` record (package `com.mmx.order.application.service`); `ExecuteOrderService` loses `propagateHubOutcome` + `routingContextFor` and gains a port dependency; `OrderLifecycleService` loses `propagateHubCancelOrReject` and gains a port dependency.
  - `mmx-domain`: new `RoutedOrderPairIntegrityException` (invariant-violation semantics, distinct from `InvalidOrderException`).
  - `mmx-bootstrap`: new `TransactionalOrderLifecycleUseCase` (wraps cancel + reject); `OrderModuleConfiguration` rewires `cancelOrderUseCase` / `rejectOrderUseCase` beans and adds the propagation port/impl beans.
  - Tests: new `RoutedOrderOutcomePropagationServiceTest` (the single test surface for all three outcomes, including the previously-untested cancel/reject); `ExecuteOrderServiceTest` / `OrderLifecycleServiceTest` updated to mock the port and to reflect the new error policy.
- **APIs / contracts:** none. No OpenAPI change; the trader-facing REST surface is unchanged.
- **Specs:** `order-routing` delta — strengthened propagation requirement (broken-pair + atomicity).
- **ADRs:** references ADR-0002 (the pair-existence and synchronous-sync invariant). No new ADR created now; capture decisions in `design.md`, elevate to ADR-0005 only if still load-bearing after implementation.
- **Full grilling decisions + current/target module maps + sequence diagrams:** `.scratch/architecture-deepening/02-routed-outcome-propagation-architecture.md`.
