# Back-office async integration (contract-first)

**Canonical AsyncAPI 3 spec (contract-first)**: [asyncapi.yaml](./asyncapi.yaml) — **v1.0.0** defines mmx → back-office execution handoff on Kafka.

**Canonical OpenAPI 3 spec (sync)**: [openapi.yaml](./openapi.yaml) — Trader product API and inbound `POST …/back-office/orders/{orderId}/accounted`.

## Contract-first rule

All **product and integration wire formats** for trader and back-office communication in this feature MUST be defined in machine-readable contracts **before** implementation:

| Transport | Canonical artifact | Prose mirror |
|-----------|-------------------|--------------|
| HTTP (sync) | `openapi.yaml` | `api-v1.md` |
| Kafka (async) | `asyncapi.yaml` | `asyncapi-v1.md` (this file) |

Runtime HTTP payloads and Kafka message bodies MUST conform to these contracts in the same delivery as behaviour changes.

## Channel: `mmx.order.executed`

- **Publisher**: mmx (after transactional outbox relay).
- **Consumer**: back-office application (out of mmx scope).
- **Key**: `orderId` (UUID string) — partition key and idempotency key.
- **Body**: `OrderExecutedV1` JSON (thin handoff DTO).

## Message: `OrderExecutedV1`

**Payload schema (canonical file)**: [schemas/OrderExecutedV1.json](./schemas/OrderExecutedV1.json) — referenced by `asyncapi.yaml` via `$ref`; single source of truth for integration tests and Redpanda Schema Registry.

Contains execution and booking fields frozen at handoff schedule time so the consumer can book without calling mmx for facts or relying on trader re-entry.

Required fields: `eventType` (const `OrderExecutedV1`), `orderId`, `executedAt`, `orderType`, `orderOperation`, `portfolioNumber`, `currency`, `amount`, `valueDate`, `executedRate`, `counterparty`, `dealingReference`, `contractNumber`, `externalOrderReference`.

Optional: `tenor`, `noticePeriod` (nullable).

### Routing context (routed trades only)

For a **routed** trade (TradingClient intake executed at the hub), mmx emits **exactly one** `OrderExecutedV1` from the **hub-side** `orderId`. The payload includes the hub booking facts plus an optional **routing-context block** so the back office can build both hub-side and originating client-side contracts without reading MMX routing tables:

| Field | Description |
|-------|-------------|
| `routingId` | Deterministic correlation id (UUID) shared by client-side and hub-side order rows |
| `originatingLegalEntityCode` | TradingClient `LegalEntityCode` |
| `clientOrderId` | Client-side order UUID |
| `clientPortfolioNumber` | Client PM portfolio at routing time |
| `clientCounterparty` | Client delegated-institution display name (e.g. `BNP via LOC`) |

Native (non-routed) hub desk orders **omit** the routing-context block; behaviour is unchanged from v1.

The propagated client-side `EXECUTED` transition does **not** emit a separate back-office event — a scoped exception to the one-outbox-row-per-`EXECUTED` rule.

## Consumer idempotency

Duplicate deliveries with the same `orderId` MUST be handled idempotently by the back-office consumer (at-least-once Kafka semantics).

## Channel: `mmx.oncall.rate.handoff`

- **Publisher**: mmx (after transactional outbox relay for OnCall rate segments).
- **Consumer**: back-office application (out of mmx scope).
- **Key**: `segmentId` (UUID string) — partition key and idempotency key for both message types.
- **Body**: `OnCallRateUpdatedV1` or `OnCallRateCanceledV1` JSON (discriminated by `eventType`).

## Message: `OnCallRateUpdatedV1`

**Payload schema (canonical file)**: [schemas/OnCallRateUpdatedV1.json](./schemas/OnCallRateUpdatedV1.json)

Required fields: `eventType` (const `OnCallRateUpdatedV1`), `segmentId`, `institution`, `currency`, `noticePeriod`, `rate`, `valueDate`.

Does **not** include `priorEndDate` — the consumer derives the superseded prior end as `valueDate − 1`.

## Message: `OnCallRateCanceledV1`

**Payload schema (canonical file)**: [schemas/OnCallRateCanceledV1.json](./schemas/OnCallRateCanceledV1.json)

Required fields: `eventType` (const `OnCallRateCanceledV1`), `segmentId`.

Observability / early-discard signal when a trader cancels a pending segment; confirmation safety is enforced by the inbound HTTP compare-and-set on `segmentId`.

## OnCall consumer idempotency

Duplicate deliveries with the same `segmentId` MUST be handled idempotently by the back-office consumer (at-least-once Kafka semantics).

## Versioning

Breaking payload changes require a new message version (e.g. `OrderExecutedV2`) and compatibility policy documented in the AsyncAPI spec and OpenSpec delta specs.
