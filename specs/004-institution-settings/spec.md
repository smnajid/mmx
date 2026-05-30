# Feature 004 — Institution settings

Trader-maintained catalog of onboarded **institutions** (banks) for desk settings and execution selection. See OpenSpec change `institution-onboarding-rates` for capability deltas and `design.md` in that change for architecture.

## Scope

- REST API under `/api/v1/settings/institutions` (contract-first OpenAPI)
- System-generated immutable `institutionCode` (`{ACRONYM}-{nn}`) from trader `displayName` only on onboard
- Active/inactive lifecycle (no hard delete)
- Execute enforcement via `order-institution-constraints` (delta on feature 002)

## Ubiquitous language

- **Institution** — catalog row in Settings (`institutionCode`, `displayName`, `active`)
- **Counterparty** — order/execution field populated from institution `displayName` at execute

## Out of scope

- Term/OnCall rate tables and CSV upload
- Institution delete; LEI; hierarchies
- Intake-side institution field
