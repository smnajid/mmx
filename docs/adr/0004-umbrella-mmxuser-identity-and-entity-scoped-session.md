# Umbrella MMXUser identity + role + entity-scoped session, replacing X-Trader-Id

**Status:** accepted

Every human who logs into MMX is an **MMXUser** identified on requests by a single umbrella header **`X-User-Id`** (replacing the legacy `X-Trader-Id`), carrying a **role** (`Trader` | `ClientRepresentative` in V1). An MMXUser holds N allowed `(LegalEntity, role)` scopes; a session is bound to one active scope chosen at login and switchable by re-scope, and the server never accepts a free per-request entity choice. We rejected parallel per-role identity headers (`X-Trader-Id` + `X-Client-Rep-Id`) so that adding future human roles is a new role value, not a new header/auth path, and so authorisation is one mechanism — `(user, role, LegalEntity)` — instead of two that drift.

## Considered options

- **Parallel per-role headers** — rejected: every new human role means a new header in OpenAPI, a new controller mapping, and a parallel auth path; pretends different roles are different kinds of identity when they are one kind with different permissions.
- **Single `X-User-Id` + role + entity-scoped session (chosen).**

## Consequences

- `X-Trader-Id` is replaced by `X-User-Id`; the desk contract gains a role dimension. Trader identity still matters functionally (assignment links one Trader to one order) but is now "the MMXUser with role Trader assigned to this order".
- A TradingHub's users are Traders; a TradingClient's users are ClientRepresentatives (Settings-only).
- Intake from Portfolio Management is unaffected by this model: PM is a system caller per Organisation, so the order's `legalEntityCode` is a required body field (ADR-0003 / CONTEXT.md), not a session scope.
