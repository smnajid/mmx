# Spec Delta

## MODIFIED Requirements

### Requirement: OrderExecutedV1 is a thin handoff DTO with idempotent consumer semantics

The `OrderExecutedV1` message SHALL include only fields required for back-office booking (per `asyncapi.yaml`). The `eventType` field SHALL be the constant `OrderExecutedV1`.

- **Every message** SHALL carry `institutionCode` and `counterpartyAccount`: the order's institution and its **counterparty account snapshot** for the order's OrderType, as it stood at execution, at the LegalEntity that owns the order.
- **A routed trade** SHALL additionally carry a routing-context block: `routingId`, `originatingLegalEntityCode`, `clientOrderId`, `clientPortfolioNumber`, `clientCounterparty`, and `clientCounterpartyAccount` (the client's snapshot, taken at routing). With it, the back office can build both the hub-side and the originating client-side contracts, each against its own counterparty account, and correlate contract reversal/replace by `routingId`, without reading any MMX routing table.
- **A native (non-routed) order** SHALL omit the routing-context fields.

The new fields SHALL be backward-compatible optional properties in the schema. The back-office consumer contract (documented in `asyncapi-v1.md`) SHALL require idempotent processing keyed by `orderId`. MMX SHALL NOT require the consumer to call MMX HTTP APIs to obtain execution facts.

#### Scenario: Message is self-contained for booking

- **WHEN** a back-office consumer processes a valid `OrderExecutedV1` message for the first time
- **THEN** it can book using fields in the message body, including `counterpartyAccount`, without a follow-up MMX read

#### Scenario: Snapshot is not rewritten by a later account change

- **WHEN** an order is executed while `LOC`'s Term counterparty account for `BNP` is `LOC-BNP-T`, and the account is changed before the outbox relay publishes
- **THEN** the published message carries `counterpartyAccount` `LOC-BNP-T`

#### Scenario: Duplicate delivery is safe

- **WHEN** the same `orderId` message is delivered more than once
- **THEN** duplicate processing does not create duplicate booking side effects (consumer responsibility documented in async contract)

#### Scenario: Routed message carries routing context

- **WHEN** a routed `OrderExecutedV1` is observed on `mmx.order.executed`
- **THEN** its body includes the routing-context block, with the following values:
  - `routingId` matches the link;
  - `clientOrderId`, `clientPortfolioNumber`, and `clientCounterparty` are the originating client-side order's values;
  - `clientCounterpartyAccount` is the client's snapshot stored on the hub-side order.

#### Scenario: Native message omits routing context

- **WHEN** a native (non-routed) `OrderExecutedV1` is observed
- **THEN** its body omits the routing-context fields

## ADDED Requirements

### Requirement: Institution export is recorded in the transactional outbox

Whenever an institution's exported state changes, the system SHALL insert exactly one institution-export outbox row in the **same database transaction** as the change. The following changes trigger a row:

- onboarding (hub-native or client);
- re-onboarding;
- a counterparty-account change;
- client offboarding;
- hub-institution deactivation or reactivation.

The row's payload SHALL be the JSON serialisation of **`InstitutionUpdatedV1`** (per `asyncapi.yaml`), frozen at record time. A request that changes no exported field SHALL NOT record a row. Grant changes and client-enablement changes SHALL NOT record institution-export rows. The settings REST call SHALL succeed regardless of Kafka availability, and SHALL NOT call the back office synchronously.

#### Scenario: Onboarding commits institution and export together

- **WHEN** a ClientRepresentative on `PAR` onboards `BNP via LOC`
- **THEN** the institution row and exactly one `InstitutionUpdatedV1` outbox row for `(PAR, BNP via LOC)` are committed together

#### Scenario: Unchanged accounts record nothing

- **WHEN** a Trader saves `BNP`'s counterparty accounts with the values already stored
- **THEN** no institution-export outbox row is recorded

#### Scenario: Export does not depend on Kafka

- **WHEN** a Trader deactivates `BNP` while Kafka is unavailable
- **THEN** the HTTP response is success and the export remains pending or becomes `FAILED` per relay rules, and the deactivation is not rolled back

---

### Requirement: Institution export is delivered only to the owning LegalEntity's back office

The relay SHALL publish each `InstitutionUpdatedV1` to the topic of the institution's owning LegalEntity, `mmx.institution.{legalEntityCode}` (for example `mmx.institution.LOC` and `mmx.institution.PAR`). The message key SHALL be `institutionCode`. A LegalEntity's institution state SHALL NOT be published to another LegalEntity's topic. The relay SHALL follow the same producer-ack, retry, and terminal-`FAILED` rules as the other back-office outboxes.

#### Scenario: Same bank, two LegalEntities, two topics

- **WHEN** `LOC` changes `BNP`'s accounts and `PAR` changes `BNP via LOC`'s accounts
- **THEN** `LOC`'s event is published only to `mmx.institution.LOC` and `PAR`'s only to `mmx.institution.PAR`

#### Scenario: Row marked SENT only after ack

- **WHEN** the relay publishes an `InstitutionUpdatedV1` and the broker acknowledges it
- **THEN** the outbox row is marked `SENT`; without an acknowledgement it stays pending for retry

---

### Requirement: InstitutionUpdatedV1 is a full-state DTO with latest-wins consumer semantics

`InstitutionUpdatedV1` SHALL carry the institution's **full exported state**, not a delta:

- `eventId`, and `eventType` (the constant `InstitutionUpdatedV1`);
- `legalEntityCode`, `institutionCode`, `displayName`;
- `hubLegalEntityCode` and `hubInstitutionCode`: present for a client-onboarded institution, absent for a hub-native one;
- `termCounterpartyAccount` and `onCallCounterpartyAccount`: nullable;
- `closedToNewBusiness`;
- `changeReason`: one of `ONBOARDED`, `REONBOARDED`, `ACCOUNTS_CHANGED`, `OFFBOARDED`, `DEACTIVATED`, `REACTIVATED`;
- `version`: strictly increasing per `(legalEntityCode, institutionCode)`;
- `occurredAt`.

The consumer contract (documented in `asyncapi-v1.md`) SHALL require latest-wins processing by `version`, ignoring any message whose `version` is not greater than the last applied one. When `closedToNewBusiness` is true, it SHALL tell the back office to open no new contracts while continuing to service existing ones.

#### Scenario: Offboarding export carries full state

- **WHEN** `PAR` offboards `BNP via LOC`, which has Term account `PAR-BNP-T` and no OnCall account
- **THEN** the message carries `closedToNewBusiness` true, `changeReason` `OFFBOARDED`, `termCounterpartyAccount` `PAR-BNP-T`, `onCallCounterpartyAccount` null, and hub link `(LOC, BNP)`

#### Scenario: Out-of-order delivery is safe

- **WHEN** the consumer receives version 3 and then a redelivered version 2 of `(PAR, BNP via LOC)`
- **THEN** version 2 is ignored per the documented consumer contract
