## Context

Execution today allocates a fresh **dealing reference** per execution via `ReferenceGenerator.generateDealingReference()`. The **contract number** recorded on `ExecutionDetails` is either **newly generated** (`generateContractNumber()`) for **SUBSCRIPTION**, or the persisted **`sourceContractNumber`** for lifecycle operations (**INCREASE** / **DECREASE** / **REDEMPTION**) — without calling `generateContractNumber()`. `ExecuteOrderService` orchestrates this before `MoneyMarketOrder.execute(..., dealingReference, generatedContractNumber, ...)`.

Lifecycle orders (**INCREASE**, **DECREASE**, **REDEMPTION**) already carry `sourceContractNumber` from intake; domain creation rejects lifecycle ops without it (`MoneyMarketOrder` / `validateSourceContractNumber`). **SUBSCRIPTION** treats `sourceContractNumber` as **optional** at the wire; if PM sends it anyway, MM **normalizes at receive** so it is **not persisted**—contract identity for subscriptions comes only from execution allocation. Minting a **new** contract number on execute contradicts the business rule that lifecycle operations apply to an **existing** MM contract.

The HTTP surface stays stable: response fields such as `generatedContractNumber` remain; semantics narrow so that for lifecycle ops it equals the sourced contract reference.

## Goals / Non-Goals

**Goals:**

- In `mmx-application`, branch contract-number resolution on `order.getOrderOperation()`: **SUBSCRIPTION** → `referenceGenerator.generateContractNumber()`; lifecycle ops → use `order.getSourceContractNumber()` and **do not** call `generateContractNumber()` for that execution path.
- Keep **dealing reference** behaviour as today unless a separate requirement says otherwise (still generated per execute via `generateDealingReference()`).
- Preserve dependency direction: application service orchestrates ports and domain; `ReferenceGenerator` port unchanged.
- Update automated tests at the narrowest failing layer (`ExecuteOrderServiceTest` first; integration/e2e where behaviour is asserted).
- Align trader-facing contract prose (`specs/002-trader-orders-views/contracts/openapi.yaml`, `api-v1.md`) if descriptions currently imply every execution mints a new contract id.
- On **SUBSCRIPTION** receive, normalize intake so `sourceContractNumber` is **not stored** (pass **null** into domain create): **ignored if present**, avoids misleading reads and matches execution semantics in `specs/execution-contract-number/spec.md`.

**Non-Goals:**

- Changing **Portfolio Management’s** published API contract, required fields, or **their** validation behaviour outside this MM service (PM remains the upstream owner of what they send).
- Renaming REST or persistence fields.
- Changing how back-office or accounting uses execution details beyond reflecting the corrected contract reference.

## Decisions

| Decision | Choice | Rationale | Alternatives considered |
|----------|--------|-----------|-------------------------|
| Where to branch | **`ExecuteOrderService`** resolves contract number before calling `order.execute(...)` | Single orchestration point; keeps `MoneyMarketOrder.execute` signature stable and domain ignorant of “generator vs reuse” | Push logic into domain (`execute` picks source vs generated): couples domain to allocation policy; rejected for this POC |
| Lifecycle contract value | **`ContractNumber` from `MoneyMarketOrder.getSourceContractNumber()`** | Matches proposal and existing intake invariant | Duplicate field on command: unnecessary; source is already on aggregate |
| Domain guard on execute | **Optional assert**: if operation ∈ lifecycle set, require non-null `sourceContractNumber` before bind | Defence in depth if data ever diverges; creation already enforces for new orders | Rely only on creation-time validation: weaker if persistence/load paths evolve |
| Subscription intake `sourceContractNumber` | **`ReceiveOrderService`** (or command mapping feeding it) coerces to **null** before `MoneyMarketOrder.create` | Single boundary; persisted aggregate never carries spurious source for subscriptions; aligns with delta spec and execute-only allocation | Store PM value but ignore at execute: weaker UX and risks traders reading stored noise |
| Dealing reference | **Continue generating per execution** | Proposal scopes contract number only | N/A |

**Adapter / module placement**

- **Application**: `backend/mmx-application/.../ExecuteOrderService.java` — conditional contract number.
- **Application**: `backend/mmx-application/.../ReceiveOrderService.java` — **SUBSCRIPTION**: normalize `sourceContractNumber` to null before domain create.
- **Domain**: `backend/mmx-domain/.../MoneyMarketOrder.java` — optional tighten of `execute` preconditions for lifecycle ops only if we want explicit domain errors when source is missing at execute time.
- **Port**: `ReferenceGenerator` in `mmx-application/.../port/out/ReferenceGenerator.java` — no API change; `UuidReferenceGenerator` unchanged.
- **Tests**: `mmx-application/src/test/.../ExecuteOrderServiceTest.java`, `ReceiveOrderServiceTest.java` (unit); broader tests under `mmx-bootstrap` e2e / REST integration as needed.

**HTTP / codegen**

- Canonical contract: `specs/002-trader-orders-views/contracts/openapi.yaml` and mirror `api-v1.md`; regenerate OpenAPI server interfaces/models if descriptions or schemas change. REST adapters implement generated API interfaces in `mmx-adapter-in-rest`.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Tests assumed UUID-shaped “new” ids for every execution | Update mocks and assertions: lifecycle paths verify **no** `generateContractNumber()` interaction and correct propagated value |
| Field name `generatedContractNumber` reads misleading for lifecycle | Document semantics in OpenAPI **`description`** and `api-v1.md`; no rename in this change |
| Stale orders missing source in DB (should not happen given rules) | Domain or application guard throws clear `InvalidOrderException` on execute for lifecycle without source |

## Migration Plan

- **Data**: No Flyway migration expected; behaviour-only change.
- **Deploy**: Single application rollout; no flag required unless product wants a kill-switch (out of scope).
- **Rollback**: Redeploy previous artefact; lifecycle executions during the bad window would have stored wrong contract ids — identify by timestamp/version if correction ever needed (operational, not automated here).

## Open Questions

- Should **dealing reference** ever be reused for lifecycle ops? Current assumption: **no**, still one new dealing reference per execution.
- Any downstream consumer (reports, accounting handoff) that assumed “new UUID contract id implies subscription”? If yes, coordinate messaging or filters; **back-office-accounting-handoff** specs may need a cross-check after implementation.
