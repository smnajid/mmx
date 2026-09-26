# Institution & rates settings programme (hybrid C)

Delivery is split into **three OpenSpec changes**. Specs and design for later phases may be drafted early, but **implementation merges** should follow this order unless explicitly waived.

| Phase | OpenSpec change | Delivers | Depends on |
|-------|-----------------|----------|------------|
| **1** | [`institution-onboarding-rates`](.) (this folder — phase 1 scope) | Onboarded institution catalog, settings REST/UI, settings navigation entry | `managed-currencies-settings` (done) |
| **2** | [`term-rate-daily-upload`](../term-rate-daily-upload/) | Morning CSV upload, Term rate persistence per trading day | Phase 1 |
| **3** | [`oncall-rate-curve-handoff`](../oncall-rate-curve-handoff/) | OnCall curve segments (value/end date, Valid/Canceled), Kafka handoff to back office | Phase 1; managed currencies for ISO codes |

**Out of programme scope (for now):** auto-binding Term rates to intake/execute; full curve analytics; PM-initiated rate feeds; institution-settings UX polish (may follow as a separate change like `currency-settings-ux`).

**Alignment gate:** Product decisions listed in [`OPEN-DECISIONS.md`](OPEN-DECISIONS.md) must be resolved (or explicitly deferred with a default) before locking phase 2/3 specs and contracts. Phase 1 can proceed with only institution-catalog decisions.
