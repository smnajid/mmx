## ADDED Requirements

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
