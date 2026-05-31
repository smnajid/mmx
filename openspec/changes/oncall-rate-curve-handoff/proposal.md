## Why

OnCall rates change when institutions notify the desk. Traders must record **curve point updates** (value date, supersede prior segment end date, Valid/Canceled) and **notify back office** so they can refresh curves and impact open contracts—reusing the transactional outbox + Kafka pattern from execution handoff.

**Programme context:** Phase 3 of hybrid C — see [`../institution-onboarding-rates/PROGRAMME.md`](../institution-onboarding-rates/PROGRAMME.md). Open product/technical choices: [`../institution-onboarding-rates/OPEN-DECISIONS.md`](../institution-onboarding-rates/OPEN-DECISIONS.md) (O-01–O-05).

## What Changes

- **OnCall rate segments** per institution (curve key per O-01): create/update point with **value date**; close superseded segment **end date**; status **Valid** | **Canceled**.
- Trader REST + UI to maintain curve points (institution-scoped).
- **Contract-first AsyncAPI** message (e.g. `OnCallRateUpdatedV1`) + outbox row in same transaction as persist; relay to Kafka with idempotent consumer semantics (delta on `back-office-outbound-messaging`).

**Out of scope:**

- Term CSV upload (phase 2).
- Full historical curve charts / analytics.
- PM-initiated rate feeds.

No **BREAKING** order lifecycle changes unless separately decided.

## Capabilities

### New Capabilities

- `oncall-rate-curve-management`: Value-dated OnCall rate segments with Valid/Canceled lifecycle and supersede rules.

### Modified Capabilities

- `back-office-outbound-messaging`: New Kafka handoff for OnCall rate updates (schema, channel, outbox relay).

## Impact

- **Contracts**: OpenAPI for curve maintenance; AsyncAPI + prose mirror for rate-updated event; canonical JSON schema (same discipline as `OrderExecutedV1`).
- **Backend**: `OnCallRate` segment entity; domain rules for end-date/status; outbox + mapper in `mmx-adapter-out-messaging`.
- **Frontend**: OnCall rate editor per institution (under settings).
- **Tests**: Domain segment tests; outbox/Kafka integration test; Vitest for editor flows.
- **Dependencies**: Phase 1 institutions; managed currencies; phase 2 not required for OnCall logic but programme order is 1 → 2 → 3 for team focus.

## Gate before `/opsx:continue` (specs/design)

Resolve or defer with defaults: **O-01**–**O-05** (especially segment dating and Kafka payload with back office).
