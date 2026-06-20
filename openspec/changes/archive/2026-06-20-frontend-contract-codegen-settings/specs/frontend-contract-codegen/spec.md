## MODIFIED Requirements

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
