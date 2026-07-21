# Design — Routed-order pair outcome-propagation

Full grilling decisions, current/target module maps, and sequence diagrams live in [.scratch/architecture-deepening/02-routed-outcome-propagation-architecture.md](../../../.scratch/architecture-deepening/02-routed-outcome-propagation-architecture.md). This document records the load-bearing technical decisions for implementation; the scratch artifact is the rationale source.

## Context

ADR-0002 mandates that a routed order is **two linked records** correlated by a routing id, and that "status must be synchronised: hub trader execution synchronously sets the client-side order `Executed` in the same transaction; terminal outcomes propagate." Today this is implemented as two private methods smeared across `ExecuteOrderService` (`propagateHubOutcome` + `routingContextFor`) and `OrderLifecycleService` (`propagateHubCancelOrReject`), with three gaps:

1. **Untested cancel/reject propagation** — `propagateCancelFromHub` / `propagateRejectFromHub` exist on `MoneyMarketOrder` and are exercised by **no** unit test (`OrderLifecycleServiceTest` uses only hub-native orders).
2. **Silent desync on broken pair** — cancel/reject use `ifPresent` and silently no-op when the client-side order is missing (a corrupt pair); execute throws `InvalidOrderException`. Asymmetric and invisible to ops.
3. **No transaction boundary on cancel/reject** — `ExecuteOrderService` runs inside `TransactionalExecuteOrderUseCase`; `OrderLifecycleService` has no wrapper, so hub and client saves can commit independently.

Hub execute also fetches the client-side order **twice** (once in `propagateHubOutcome`, once in `routingContextFor`) for the same routing id.

Stack: Spring Boot 4, hexagonal (`mmx-domain` → `mmx-application` → adapters → `mmx-bootstrap`). Contract-first OpenAPI; **no REST surface change** in this change. TDD is the default per AGENTS.md.

## Goals / Non-Goals

**Goals:**
- One application-internal port (`RoutedOrderOutcomePropagation`) owns hub→client terminal-outcome propagation across execute / cancel / reject, with a single test surface.
- Enforce the ADR-0002 pair-existence invariant uniformly: a missing linked client-side order throws `RoutedOrderPairIntegrityException` on **all three** outcomes.
- Make cancel/reject terminal sync atomic with the hub save (new `TransactionalOrderLifecycleUseCase`).
- Eliminate the double client-order fetch on hub execute.
- Deliver via strict TDD (red → green → behavior-preserving refactor → red → green), per AGENTS.md and the `tdd` skill.

**Non-Goals:**
- The routing **correlation model** (replacing the `routingId` + nullable `originatingLegalEntityCode` sentinel with an explicit `RoutedOrderLink`) — candidate #3, deferred.
- `OrderRepository` fat-port decomposition — candidate #8, deferred.
- Consolidating the per-`(operation, side, stage)` **ContractNumber** rule into one module — candidate #4, deferred. The client-side contract-number rule moves as-is into the propagation impl; `resolveExecutionContractNumber` stays in `ExecuteOrderService`.
- Any REST / OpenAPI / `api-v1.md` change. The trader-facing surface is unchanged.
- Any frontend, Flyway, or DB-schema change.
- Propagation of `Accounted` — out of scope by CONTEXT L121/L127 (`Accounted` is reached independently per side via separate back-office callbacks; not a desk outcome).
- A new ADR. Decisions are captured here; elevate to ADR-0005 only if still load-bearing after implementation (matches item #1's decision #8).

## Decisions

### Decision 1 — Port interface, not a concrete class

`RoutedOrderOutcomePropagation` is a port interface in `com.mmx.order.application.service`, with a single implementation `RoutedOrderOutcomePropagationService`, mocked in tests.

**Why a port here when item #1 made `RoutedOrderIntake` a concrete class (#1 decision #4):** #1 had a **single** caller (`IntakeService`) — "one adapter = hypothetical seam" applied. Here there are **two** real callers (`ExecuteOrderService`, `OrderLifecycleService`) already, and the headline benefit is a testable seam for the cancel/reject path that today has zero tests. The port matches the existing mocked-seam pattern (`OrderRepository.findRoutedClientOrderByRoutingId` is already mocked in `ExecuteOrderServiceTest`).

**Alternatives considered:** concrete class (consistency with #1, but loses the test-leverage benefit that is the point of the change); port in `port/out/` (rejected — it's application-internal, not adapter-backed).

### Decision 2 — Port owns propagation + the outbox routing context for execute

`propagateExecution` returns `ExecutionPropagationResult(MoneyMarketOrder clientOrder, ExecutionHandoffRoutingContext handoffContext)`; `propagateCancel` / `propagateReject` are `void`. `ExecuteOrderService.routingContextFor` is deleted; the outbox context is a byproduct of propagation (the port already holds the client order).

**Why:** kills the double client-order fetch on execute (today `findRoutedClientOrderByRoutingId` runs twice for the same id). Coherent port contract: "apply the terminal outcome; for execute, also return what the outbox needs." Outbox **scheduling** stays in `ExecuteOrderService` — the port does not touch messaging (avoids blowing the module's scope).

**Alternatives considered:** port owns propagation only (smaller port, but the double-fetch smell survives); port also schedules the outbox (rejected — couples propagation to messaging and pulls `suppressesExecutionHandoff` logic into the port).

### Decision 3 — Throw `RoutedOrderPairIntegrityException` on a missing client, on all three outcomes

New `mmx-domain` exception `RoutedOrderPairIntegrityException`, thrown when `findRoutedClientOrderByRoutingId` returns empty for a hub-side order with `routingId != null` and a terminal status. Replaces the current asymmetry (execute threw `InvalidOrderException`; cancel/reject silently no-op'd).

**Why:** ADR-0002 makes the pair an invariant. A missing client is **data corruption**, not "nothing to update." The current silent skip on cancel/reject is a latent desync (hub `Cancelled`, client stuck `Routed` forever). A distinct exception (vs reusing `InvalidOrderException`) keeps `InvalidOrderException` semantically for **user-input** errors and is ops-alertable rather than trader-retryable. Uniform across all three outcomes → one test surface asserts the invariant.

**Behavior change (flagged in proposal):** hub cancel/reject on a corrupted pair now throws instead of silently completing. Only triggers in a state that already violates ADR-0002; surfacing it is the fix.

**Alternatives considered:** uniform throw with `InvalidOrderException` (rejected — wrong semantics; an invariant violation is not a 400-style user error); keep silent on cancel/reject (rejected — leaves the desync gap open and makes the invariant unenforceable on two of three paths).

### Decision 4 — Client-side contract-number rule moves as-is; `resolveExecutionContractNumber` stays

The four-line client-side contract-number rule (subscription → `referenceGenerator.generateContractNumber()`; lifecycle → reuse `clientOrder.getSourceContractNumber()`) relocates verbatim from `propagateHubOutcome` into `RoutedOrderOutcomePropagationService`. `ExecuteOrderService.resolveExecutionContractNumber` (the **hub-side** rule) is untouched.

**Why:** surgical scope (#1 decision #6 / Karpathy). The two rules share a *shape* but operate on different orders/sources — not pure duplicates; candidate #4 designs the right "per `(operation, side, stage)`" abstraction. The "routed subscription = two contract numbers" invariant is still assertable through this change's port result (`ExecutionPropagationResult.clientOrder`), so #4 can later harden the rule shape without breaking these tests.

**Alternatives considered:** extract a `ContractNumberResolver` collaborator now (rejected — folds part of #4 into #2, mingling refactors); defer via a pass-through port (rejected — premature seam #4 hasn't designed).

### Decision 5 — `TransactionalOrderLifecycleUseCase` (one wrapper for cancel + reject)

New `mmx-bootstrap` class implementing both `CancelOrderUseCase` and `RejectOrderUseCase`, `@Primary` `@Service` `@Transactional`, delegating to `OrderLifecycleService`. Mirrors `TransactionalExecuteOrderUseCase`. `OrderModuleConfiguration` rewires `cancelOrderUseCase` / `rejectOrderUseCase` beans to the wrapper.

**Why:** closes the cancel/reject atomicity gap — without it, Decision 3 only catches *missing* clients, not *failed client saves*. One wrapper matches `OrderLifecycleService`'s own shape (it already implements both use-cases), so the wrapper is a clean 1:1 delegate. Keeps `@Transactional` out of `mmx-application` (matches the established pattern and #1 decision #3). Retires the smaller PRD item "ReceiveOrderUseCase has no equivalent wrapper — inconsistent" alongside #1's `TransactionalIntakeUseCase`.

**Alternatives considered:** two separate wrappers `TransactionalCancelOrderUseCase` + `TransactionalRejectOrderUseCase` (rejected — near-duplicate classes for a service that already combines both); `@Transactional` on `OrderLifecycleService` itself (rejected — violates "keep `@Transactional` out of `mmx-application`").

### Decision 6 — Migration: lock → extract (behavior-preserving) → flip policy → add tx wrapper

1. **Red** (lock current behavior): tests asserting today's `propagateHubOutcome` (execute → client propagated; two-numbers) and `propagateHubCancelOrReject` (cancel/reject → client propagated; **silent-skip on missing client**). These pass against today's code.
2. **Refactor (behavior-preserving):** extract the port + impl; `ExecuteOrderService` / `OrderLifecycleService` inject it and become thin; delete `routingContextFor`. Locking tests stay green.
3. **Red** (behavior change): update cancel/reject missing-client tests from silent-skip to `RoutedOrderPairIntegrityException`; add execute missing-client throws the same exception (replacing `InvalidOrderException`).
4. **Green:** throw `RoutedOrderPairIntegrityException` on all three outcomes.
5. **Tx wrapper:** add `TransactionalOrderLifecycleUseCase`; rewire beans; add an atomicity assertion for cancel/reject.

**Why:** matches #1 decision #2 and AGENTS.md TDD. Locking current behavior first proves the extraction is behavior-preserving; the behavior change (Decision 3) is the riskiest, trader-visible part and gets its own attributable red→green step.

**Alternatives considered:** behavior-change-first (rejected — no safety net proves the extraction didn't accidentally change behavior before we intentionally change it); combined refactor + behavior change (rejected — mingled, harder to review, violates surgical-changes).

### Decision 7 — Naming and package

`RoutedOrderOutcomePropagation` (port) + `RoutedOrderOutcomePropagationService` (impl) + `ExecutionPropagationResult` (return record), all in `com.mmx.order.application.service`. `RoutedOrderPairIntegrityException` in `com.mmx.order.domain.exception`.

**Why:** "Routing outcome propagation" is the CONTEXT ubiquitous-language *concept* (L123–124); the module names the *entity* it operates on (a routed order). Drops redundant "Pair" (every routed outcome is inherently a pair operation per ADR-0002). `...Service` suffix matches every neighbor in that package. ArchUnit (`ArchitectureRules.layeredArchitectureRules`) confirms an interface in `application..` is clean: Application may access Application + Domain; `RoutedOrderIntake` already lives in that package from #1.

### Decision 8 — No CONTEXT.md update, no new ADR now

"Routing outcome propagation" is already in CONTEXT L123–124 (including the per-side `Accounted` rule). Module/exception/wrapper names are implementation. The pair-existence invariant and "terminal outcomes propagate" are already in ADR-0002; the throw-on-missing enforcement policy and the tx wrapper are easily reversible in code (weak on the ADR "hard to reverse" prong). Capture here; elevate to ADR-0005 only if still load-bearing after implementation. Matches #1 decisions #7 and #8.

## Risks / Trade-offs

- **[Behavior change on cancel/reject] → Mitigation:** flagged as BREAKING in the proposal and in `order-routing` delta spec; only triggers in a corrupt-pair state that already violates ADR-0002. The TDD lock-step (Decision 6) makes the change attributable and reviewable in isolation.
- **[New public seam could be re-suggested as "split" by a future architecture pass] → Mitigation:** the two-caller rationale and the test-leverage argument are recorded here and in the scratch artifact; if it still feels load-bearing post-implementation, elevate to ADR-0005.
- **[Transactional wrapper changes rollback behaviour on cancel/reject] → Mitigation:** this is the intended fix (atomic pair sync per ADR-0002); an integration-level assertion documents the new boundary. Hub-native (non-routed) cancel/reject are unaffected functionally — they just gain a transaction they should have had.
- **[Port return type couples the impl to `ExecutionHandoffRoutingContext`] → Mitigation:** `ExecutionHandoffRoutingContext` is already an `mmx-application` `port/out` record; the coupling is intra-application and benign. Outbox **scheduling** stays in `ExecuteOrderService`, so the port does not depend on `ExecutionHandoffOutbox`.
- **[Exception type proliferation in `mmx-domain`] → Mitigation:** one new type, with clear invariant-violation semantics distinct from user-input `InvalidOrderException`; justified by the uniform-enforcement goal.

## Migration Plan

TDD-ordered; see `tasks.md` for the exact scoped `mvn` commands. High level:

1. Lock current behavior (red→green on today's code) in `ExecuteOrderServiceTest` + `OrderLifecycleServiceTest`.
2. Extract port + impl behavior-preserving; thin the two services; delete `routingContextFor`. ArchUnit `HexagonalArchitectureTest` green.
3. Flip error policy: missing-client throws `RoutedOrderPairIntegrityException` on all three outcomes (red→green).
4. Add `TransactionalOrderLifecycleUseCase`; rewire beans; add atomicity assertion.
5. Final verification: full reactor `mvn test` from `backend/` (including `mmx-bootstrap` integration tests).

**Rollback:** each step is a small, independently revertible commit. The behavior-change step (3) is the only one with externally visible behavior; reverting it restores the silent-skip without reverting the extraction.

## Open Questions

None — all load-bearing decisions were resolved during grilling (`.scratch/architecture-deepening/02-routed-outcome-propagation-architecture.md` §8).
