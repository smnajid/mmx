# Cross-Org routed-order outcome (leg B, contract-first)

**Canonical AsyncAPI 3 spec (contract-first)**: [asyncapi.yaml](./asyncapi.yaml) — **v1.0.0** defines
the hub → client leg-B outcome channel for cross-org order routing.

**Canonical OpenAPI 3 spec (leg A + reference data)**: [openapi.yaml](./openapi.yaml).

## Contract-first rule

All **wire formats** for cross-org routing MUST be defined in machine-readable contracts **before**
implementation:

| Transport | Leg | Canonical artifact | Prose mirror |
|-----------|-----|--------------------|--------------|
| HTTP (sync) | A (request) + reference data | `openapi.yaml` | `api-v1.md` |
| Kafka (async) | B (outcome return) | `asyncapi.yaml` | `asyncapi-v1.md` (this file) |

Runtime Kafka message bodies MUST conform to these contracts in the same delivery as behaviour
changes.

## Channel: `mmx.routed-order-outcome.{orgCode}`

- **Owner / publisher**: the **hub deployment** (e.g. LODH), via the existing transactional outbox +
  relay. `{orgCode}` is the owning hub's `OrganisationCode` (e.g. `LODH` →
  `mmx.routed-order-outcome.LODH`).
- **Consumer**: the originating client deployment (e.g. CGED), under a **consume-only ACL**.
- **Addressing inverts**: the hub publishes to its own topic blind to who is listening; the client's
  consumer is pre-subscribed and filters `originatingLegalEntityCode ∈ {its own LegalEntities}` before
  applying. The model generalises to N remote clients on one topic.
- **Key**: composite `(originatingLegalEntityCode, routingId)` — so all outcomes for one routed pair
  share a partition and apply in order.
- **Distinct from** `mmx.order.executed` (back-office booking handoff). A remote `EXECUTED` publishes
  on **both** channels with distinct payloads: `OrderExecutedV1` (booking facts) on
  `mmx.order.executed`, and `RoutingOutcomeV1` (client-side apply) here.

## Message: `RoutingOutcomeV1`

**Payload schema (canonical file)**: [schemas/RoutingOutcomeV1.json](./schemas/RoutingOutcomeV1.json)
— referenced by `asyncapi.yaml` via `$ref`; single source of truth for integration tests and Redpanda
Schema Registry.

Type-discriminated by `outcomeType`:

| `outcomeType` | Kind | When emitted | Same-tx anchor |
|---------------|------|--------------|----------------|
| `ACCEPTED` | non-terminal | `AcceptRoutedHubOrderUseCase` creates the hub-side order | hub-side order creation (the accept signal is exactly as durable as the hub-side order) |
| `EXECUTED` | terminal | hub trader executes the remote hub-side order | hub-side `EXECUTED` transition |
| `CANCELLED` | terminal | hub trader cancels the remote hub-side order | hub-side `CANCELLED` transition |
| `REJECTED` | terminal | hub trader rejects an **existing** hub-side order | hub-side `REJECTED` transition |

Required (all variants): `eventType` (const `RoutingOutcomeV1`), `outcomeType`,
`originatingLegalEntityCode` (`^[A-Z]{3}$`), `routingId` (UUID), `occurredAt`.

Variant-specific (optional, present per the table above): `acceptedAt` (ACCEPTED); `executedAt`,
`executedRate`, `institutionCode` (hub-native), `dealingReference`, `contractNumber` (EXECUTED);
`cancelledAt` (CANCELLED); `rejectedAt`, `reason` (REJECTED).

**No deployment-internal order UUID crosses the boundary.** Cross-boundary correlation is
`(originatingLegalEntityCode, routingId)` only.

## Routing-failure reject is HTTP-only

A routing-failure reject (grant/currency/tenor invalid at the hub's `AcceptRoutedHubOrderUseCase`)
creates **no hub-side order**, so it rides **no** leg-B event. It closes `Received → Rejected` via the
leg-A HTTP `422` response directly. Only trader-`REJECTED` rides this channel.

## Silence is never terminal

The `ACCEPTED` event closes `Received → Routed` idempotently on the client side (no-op if leg A
already delivered accept) — leg B is the authoritative lifecycle mirror, leg A is the latency fast
path. A remote client-side order in `Received` is **never** auto-terminalized on transport silence.

## Consumer idempotency

`ApplyRemoteOrderOutcomeUseCase` (client side) is idempotent under at-least-once Kafka: a client order
already in the event's expected terminal/non-terminal state → no-op ack (offset advances); only a
mismatched terminal is an error. Duplicate `ACCEPTED` when already `Routed` is a no-op.

## Versioning

Breaking payload changes require a new message version (e.g. `RoutingOutcomeV2`) and a documented
compatibility policy. `RoutingOutcomeV1` registers **BACKWARD** with the Schema Registry.
