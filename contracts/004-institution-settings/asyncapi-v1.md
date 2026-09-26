# Institution Export Async Contract v1

Canonical AsyncAPI: [asyncapi.yaml](./asyncapi.yaml). Payload schema (single canonical file):
[schemas/InstitutionUpdatedV1.json](./schemas/InstitutionUpdatedV1.json), registered in the Schema
Registry under subject `mmx.institution-value` with `BACKWARD` compatibility.

## Channel: `mmx.institution.{legalEntityCode}`

- **Publisher**: MMX, through the institution-export transactional outbox and relay.
- **Consumer**: the back-office (Deposits) instance of that LegalEntity only.
- **Topic**: one per owning LegalEntity, e.g. `mmx.institution.LOC`, `mmx.institution.PAR`. `LOC`'s
  `BNP` goes only to `mmx.institution.LOC`; `PAR`'s `BNP via LOC` only to `mmx.institution.PAR`.
- **Key**: `institutionCode`.
- **Delivery**: the row is marked `SENT` only after a producer acknowledgement; otherwise it is retried
  and becomes terminal `FAILED` after the maximum attempts, like the other back-office outboxes.

## When a message is recorded

Exactly one outbox row is committed in the same database transaction as the change:

| Trigger | `changeReason` |
|---------|----------------|
| Onboarding (hub-native or client) | `ONBOARDED` |
| Re-onboarding an offboarded client institution | `REONBOARDED` |
| Counterparty-account change | `ACCOUNTS_CHANGED` |
| Client offboarding | `OFFBOARDED` |
| Hub-institution deactivation | `DEACTIVATED` |
| Hub-institution reactivation | `REACTIVATED` |

A request that changes no exported field records nothing. Grant changes and client-enablement changes
record nothing. The settings REST call never depends on Kafka and never calls the back office.

## Message: `InstitutionUpdatedV1`

A **full-state** snapshot, not a delta:

| Field | Description |
|-------|-------------|
| `eventId` | UUID of this message |
| `eventType` | Constant `InstitutionUpdatedV1` |
| `legalEntityCode` | Owning LegalEntity |
| `institutionCode` | The owning LegalEntity's institution code |
| `displayName` | Display name (derived `"{hub name} via {hub LegalEntityCode}"` for an onboarded institution) |
| `hubLegalEntityCode`, `hubInstitutionCode` | Present for a client-onboarded institution; absent for a hub-native one |
| `termCounterpartyAccount`, `onCallCounterpartyAccount` | Nullable counterparty accounts |
| `closedToNewBusiness` | `true` for a deactivated hub institution or an offboarded client institution |
| `changeReason` | One of the reasons above |
| `version` | Strictly increasing per `(legalEntityCode, institutionCode)` |
| `occurredAt` | When the change was recorded |

## Consumer contract

- **Latest wins by `version`**: apply a message only when its `version` is greater than the last
  applied version for `(legalEntityCode, institutionCode)`; ignore redeliveries and out-of-order older
  versions (e.g. version 2 after version 3).
- **`closedToNewBusiness` = true**: open no new contracts with the institution, but keep servicing
  existing ones (Decrease, Redemption, maturity).
- The export is reference data. Booking uses the **counterparty account snapshot** carried on
  `OrderExecutedV1`, never the latest exported copy.
