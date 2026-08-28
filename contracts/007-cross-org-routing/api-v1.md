# Cross-Org Routing API v1

Canonical OpenAPI: [openapi.yaml](./openapi.yaml). Exposed by the **hub deployment** (e.g. LODH) for a
remote client deployment (e.g. CGED) whose TradingClient (e.g. CGD) routes orders to this hub.

Leg-B async outcomes (LODH → CGED) are defined in the canonical AsyncAPI:
[asyncapi.yaml](./asyncapi.yaml).

## Transport-proven identity

Every operation is authenticated by a transport credential (`X-MMX-CrossOrg-Key`) that binds to
**exactly one** remote `LegalEntityCode` — the **proven `originatingLegalEntityCode`**. The gateway
derives the principal from the credential and rejects unknown credentials before any use case runs.
The payload is **never** trusted for identity. The proven principal MUST be a member of this hub's
TradingClient list (defense-in-depth: gateway early-reject + use-case membership re-check).

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/cross-org/routed-orders` | Leg-A routed-order accept (idempotent on `(originatingLegalEntityCode, routingId)`) |
| GET | `/api/v1/cross-org/reference/currencies` | Live managed-currency catalog |
| GET | `/api/v1/cross-org/reference/institutions` | Hub-native institution catalog (`activeOnly` query) |
| GET | `/api/v1/cross-org/reference/term-rates` | Term rate sheet for a `tradingDate` |
| GET | `/api/v1/cross-org/reference/grants` | Delegated grants scoped to the proven client |

## Leg A — routed-order accept

`POST /api/v1/cross-org/routed-orders` carries the CGED-resolved hub-side `portfolioNumber` (trusted,
not revalidated), a hub-native `institutionCode`, the CGED-minted `routingId`, and the order fields.

- **Accept (`200`)**: the hub validates `(institution, currency, tenor|noticePeriod)` against the
  originating client's grant in-process, creates the hub-side order in `RECEIVED`, commits a leg-B
  `ACCEPTED` outbox row in the **same transaction**, and returns `outcome = ACCEPTED` with the proven
  `originatingLegalEntityCode`, `routingId`, and `acceptedAt`.
- **Reject (`422`)**: a routing-failure (grant/currency/tenor invalid, or the proven legal entity is
  not a TradingClient member). **No hub-side order is created and no leg-B event is emitted** — the
  reject is HTTP-only. The client closes `Received → Rejected` from this response directly.

### Idempotency

The hub-side idempotency key is `(originatingLegalEntityCode, routingId)`, enforced by a partial
unique index. A leg-A retry that collides with an existing hub-side order resolves to the same
`ACCEPTED` response — no duplicate is created. `originatingLegalEntityCode` is transport-proven, so a
buggy/hostile client can only dedupe-collide with its own prior requests.

### Account trust

LODH trusts the CGED-supplied `portfolioNumber` (resolved by the client's `ExternalIdentityGateway`)
and performs no account-existence or account-ownership check. Account failures surface at booking
time, not as a routing reject. No deployment-internal order UUID crosses the boundary.

## Thin-client reference-data reads

The client deployment stores **zero** hub reference data; it reads currencies, rates, grants, and
counterparties **live from the hub** per request. Proxy indirection collapses: these endpoints return
**hub-native** institution codes, and the client renders the `"via {hub}"` display name client-side.
Grants are scoped automatically to the proven `originatingLegalEntityCode`; the client cannot claim
another client's grants.

## Error codes

`CrossOrgErrorResponse` (400/401/403): `VALIDATION_ERROR` (malformed request), `UNAUTHORIZED`
(missing/unresolvable credential), `FORBIDDEN` (membership failure — the credential resolved to a
legal entity that is not a member of this hub's TradingClient list; covers both the gateway
early-reject and the use-case membership re-check).

A routing-failure reject returns `422` with `RoutedOrderRejectResponse` (`outcome = REJECTED` +
`reason`, echoing the proven `originatingLegalEntityCode` + `routingId`), not the error envelope.
