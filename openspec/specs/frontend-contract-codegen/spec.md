# frontend-contract-codegen Specification

## Purpose

Build-time generation of the Angular app's TypeScript API types from the canonical OpenAPI contracts, so the frontend never hand-authors endpoint types that can drift from the published HTTP contract.

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

### Requirement: Contract type generation is verifiable via a single CI-ready command

The frontend SHALL provide a single command that regenerates all contract types from the canonical contracts and type-checks the project against them, exiting non-zero on any failure. This delivers guarantee that generated output is current and the project compiles against it (guarantee A). The command SHALL be runnable in CI and locally without running the unit-test suite.

#### Scenario: Verification passes on a clean tree

- **WHEN** `npm run verify:contracts` is run in `frontend/` against valid contracts and a project that compiles
- **THEN** generation completes and the type-check passes, and the command exits with status 0

#### Scenario: Verification fails on an invalid or missing contract

- **WHEN** `npm run verify:contracts` is run and a referenced contract file is missing or invalid
- **THEN** the generation step fails and the command exits non-zero

#### Scenario: Verification fails on a type mismatch against generated types

- **WHEN** the frontend source uses a contract type in a way that does not type-check against the freshly generated types
- **THEN** the type-check step fails and the command exits non-zero

### Requirement: Embeddable widget libraries generate contract types at their own build

A separately published, externally embedded frontend library (e.g. `order-creation-widget`) SHALL derive its API response types from the canonical OpenAPI contract via a build-time generator, the same as the application, but generation SHALL occur at the **library's own build** within this repository (where `contracts/` is available). The library SHALL NOT require the consuming host application to have access to `contracts/`, and SHALL NOT import generated types from the application tree.

#### Scenario: Widget response DTOs resolve to generated contract types

- **WHEN** `projects/order-creation-widget/src/lib/models/api-responses.model.ts` is inspected
- **THEN** the response shapes that exist in `002-trader-orders-views` (e.g. `OperationsResponse`, `TenorsResponse`, `NoticePeriodsResponse`, `CounterpartiesResponse`, `ContractInfoResponse`, `LiveContractsResponse`) resolve to generated contract types rather than independently hand-written interface bodies

#### Scenario: Generation runs ahead of the library build

- **WHEN** the widget library is built (`ng build order-creation-widget`)
- **THEN** contract type generation runs first (via a pre-step) so the library compiles against freshly generated `002` types

#### Scenario: Library does not depend on the host having contracts

- **WHEN** the widget library source is inspected
- **THEN** it imports its generated types only from within its own project tree (not from the application's `src/app/core/api/generated`), so the published package builds and is consumable without the host providing `contracts/`

#### Scenario: Library public domain types may stay hand-written

- **WHEN** the widget's exported public API is inspected (`public-api.ts`: `OrderCreationPayload`, `WizardStep`, and the `Tenor`/`NoticePeriod`/`OrderType`/`OrderOperation` unions)
- **THEN** these MAY remain hand-written, as they are the library's public contract with host apps and are intentionally decoupled from both host enums and the generated contract module

