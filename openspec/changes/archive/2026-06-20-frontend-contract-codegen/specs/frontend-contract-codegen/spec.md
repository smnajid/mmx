## ADDED Requirements

### Requirement: Frontend API types are generated from the canonical OpenAPI contract

The frontend SHALL derive TypeScript types for any HTTP endpoint that has a canonical OpenAPI contract from that contract via a build-time generator, rather than hand-authoring duplicate type definitions. The pilot scope of this capability is the `002-trader-orders-views` contract; the same convention applies to future contracts as they are onboarded.

#### Scenario: Types are produced from the contract

- **WHEN** the frontend `generate:api` script is run
- **THEN** TypeScript types are produced from `contracts/002-trader-orders-views/openapi.yaml` into the configured generated-output directory

#### Scenario: No hand-authored duplicate of a contracted schema

- **WHEN** `frontend/src/app/core/models/order.model.ts` is inspected
- **THEN** the `002`-contracted shapes (e.g. `OrderSummary`, `OrderDetails`, `ReceiveOrderRequest`, `ReceiveOrderResponse`, `UpdateOrderRequest`, `ExecuteOrderRequest`, `RejectOrderRequest`) resolve to the generated contract types rather than independently hand-written interface bodies

---

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
