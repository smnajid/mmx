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

Contains execution and booking fields frozen at handoff schedule time so the consumer can book without calling mmx for facts or relying on trader re-entry.

Required fields: `eventType` (const `OrderExecutedV1`), `orderId`, `executedAt`, `orderType`, `orderOperation`, `portfolioNumber`, `currency`, `amount`, `valueDate`, `executedRate`, `counterparty`, `dealingReference`, `contractNumber`, `externalOrderReference`.

Optional: `tenor`, `noticePeriod` (nullable).

## Consumer idempotency

Duplicate deliveries with the same `orderId` MUST be handled idempotently by the back-office consumer (at-least-once Kafka semantics).

## Versioning

Breaking payload changes require a new message version (e.g. `OrderExecutedV2`) and compatibility policy documented in the AsyncAPI spec and OpenSpec delta specs.
