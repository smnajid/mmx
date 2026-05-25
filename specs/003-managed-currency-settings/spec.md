# Feature 003 — Managed currency settings

Trader-maintained currency catalog and per-currency rules for money-market order intake. See OpenSpec change `managed-currencies-settings` for capability specs and `design.md` for architecture.

## Scope

- REST API under `/api/v1/settings/currencies` (contract-first OpenAPI)
- Enforcement at order receive and trader amount update (`order-currency-constraints`)
- Strict cold start: empty catalog blocks all intake until onboarding

## Out of scope

- Institution and rate settings
- PositionApi for Increase/Redemption
