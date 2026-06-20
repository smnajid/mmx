# frontend-contract-codegen Specification

## Purpose
TBD - created by archiving change frontend-contract-codegen. Update Purpose after archive.
## Requirements
### Requirement: Frontend API types are generated from the canonical OpenAPI contract

The frontend SHALL derive TypeScript types for any HTTP endpoint that has a canonical OpenAPI contract from that contract via a build-time generator, rather than hand-authoring duplicate type definitions. This convention applies to all current contracts (`002-trader-orders-views`, `003-managed-currency-settings`, `004-institution-settings`, `005-term-rate-settings`); contracts onboarded later SHALL be generated and consumed the same way.

#### Scenario: Types are produced from every current contract

- **WHEN** the frontend `generate:api` script is run
- **THEN** TypeScript types are produced into the configured generated-output directory from each of `contracts/002-trader-orders-views/openapi.yaml`, `contracts/003-managed-currency-settings/openapi.yaml`, `contracts/004-institution-settings/openapi.yaml`, and `contracts/005-term-rate-settings/openapi.yaml`

#### Scenario: No hand-authored duplicate of a contracted schema

- **WHEN** the frontend `core/api` services and `core/models` are inspected
- **THEN** shapes that exist in a canonical contract resolve to the generated contract types rather than independently hand-written interface bodies, including the `002` order, order-creation counterparty, and on-call rate segment shapes; the `003` managed-currency shapes; the `004` institution shapes; and the `005` term-rate shapes

#### Scenario: Frontend-only types may remain hand-written

- **WHEN** a type is not present in any canonical contract (e.g. paging/view helpers or header-only request shapes)
- **THEN** it MAY remain hand-written in the frontend without violating this requirement

### Requirement: Generated frontend types are regenerated each build and not committed

Generated frontend API types SHALL be treated as ephemeral build output: they SHALL be git-ignored and regenerated before the frontend is built, tested, or served, mirroring the backend's regenerate-on-build approach. Generated files SHALL NOT be edited by hand.

#### Scenario: Generated output is git-ignored

- **WHEN** the repository's ignore rules are inspected
- **THEN** the frontend generated-output directory is ignored by git and contains no committed files

#### Scenario: Generation runs ahead of build and test

- **WHEN** `npm run build` or `npm run test` is executed in `frontend/`
- **THEN** type generation runs first (via a pre-step) so the build/test uses freshly generated types

#### Scenario: Generation fails loudly on an invalid or missing contract

- **WHEN** the referenced OpenAPI contract file is missing or invalid at generation time
- **THEN** the generation step exits with a non-zero status and the build does not proceed with stale types

---

### Requirement: Frontend build remains green against generated types

Re-pointing the frontend at generated contract types SHALL preserve existing application behaviour and the public type import surface used across components and services.

#### Scenario: Frontend test suite passes after migration

- **WHEN** `npm run test` is run in `frontend/` after the `002` models are re-pointed at generated types
- **THEN** the suite passes with no type errors and existing imports of the `002` model types continue to resolve

