## Why

The Received queue currently returns **every** `RECEIVED` order for Term or OnCall with no date funnel. Traders need the **near-term default** and an explicit **show all** control described in [specs/002-trader-orders-views/spec.md](specs/002-trader-orders-views/spec.md) (User Story 3, FR-004, FR-005) and [scratch.txt](scratch.txt) lines 4–5.

## What Changes

1. **Default Received listing** restricts rows so the order’s scheduling date (`valueDate`, aligned with spec assumptions) falls in the **default near-term window**: **today and the next two calendar days**, inclusive, in **one** coherent business timezone (same as existing product rules for intake validation).
2. **Optional expansion**: A trader control (session-scoped) requests **all** Received rows for that workspace type; pagination and totals remain correct server-side.
3. **Contracts**: Extend `GET .../term/received` and `GET .../oncall/received` with a documented query parameter (or equivalent contract-first mechanism) so the SPA can request narrow vs full list without breaking existing clients.

## Capabilities

### New Capabilities

- `trader-received-queue`: Near-term window filtering and session-mode selection for Received Term/OnCall list APIs and the Angular Received screens.

### Modified Capabilities

- *(none — no established `openspec/specs/` baseline yet for this repo; requirements are captured in the delta spec below and mirrored into feature 002 artefacts during implementation per SDD.)*

## Impact

- **Backend**: `OrderQueryService`, `OrderRepository` (likely new query supporting optional date filter), generated OpenAPI stubs, integration tests.
- **Frontend**: Received list components/services; toggle UI; query param on list calls; in-memory session state only (no persistence across logout).
- **Specs/contracts**: `specs/002-trader-orders-views/contracts/openapi.yaml`, `api-v1.md`, and `tasks.md` when behaviour ships (blocking SDD parity).
