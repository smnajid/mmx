# Cross-Org Routing API v1

Canonical OpenAPI: [openapi.yaml](./openapi.yaml). Exposed by the **hub deployment** (e.g. LODH) for a
remote client deployment (e.g. CGEG) whose TradingClient (e.g. CGD) routes orders to this hub.

Leg-B async outcomes (LODH → CGEG) are defined in the canonical AsyncAPI:
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
| GET | `/api/v1/cross-org/reference/term-rates` | Term rate sheet for a `tradingDate` (grant-scoped) |
| GET | `/api/v1/cross-org/reference/term-rates/latest` | Latest Term rate per institution for `currency` + `tenor` (grant-scoped) |
| GET | `/api/v1/cross-org/reference/oncall-segments` | OnCall segments for `currency` + `noticePeriod`; open segments, or those covering `valueDate` (grant-scoped) |
| GET | `/api/v1/cross-org/reference/grants` | Delegated grants scoped to the proven client |

## Leg A — routed-order accept

`POST /api/v1/cross-org/routed-orders` carries the CGEG-resolved hub-side `portfolioNumber` (trusted,
not revalidated), the hub-native `institutionCode` linked to the client's onboarded institution, the
CGEG-minted `routingId`, the required `clientCounterpartyAccount` (the client's counterparty account
snapshot for the order's OrderType, taken at routing), and the order fields.

> **Upgrade note:** `clientCounterpartyAccount` is required and non-blank (a whitespace-only value is `400`). A client deployment on an older build gets
> `400` from a hub on this build, so both deployments must be upgraded together.

Before sending, the client validates what it owns: the institution is onboarded, open to new business,
and (Subscription/Increase) the tenor/notice period is within its effective enablement; and its own
counterparty account for the OrderType exists. A failure rejects the client-side order synchronously
as a routing failure and nothing is sent.

- **Accept (`200`)**: for a **Subscription or Increase** the hub validates `(institution, currency,
  tenor|noticePeriod)` against the originating client's grant in-process and requires the hub
  institution to be open to new business; a **Decrease or Redemption** skips both checks (it is
  accepted on a revoked grant). For **every** operation the hub institution must hold the
  counterparty account for the OrderType. The hub stores `clientCounterpartyAccount` read-only on the
  hub-side order (checking only its presence), creates the hub-side order in `RECEIVED`, commits a leg-B
  `ACCEPTED` outbox row in the **same transaction**, and returns `outcome = ACCEPTED` with the proven
  `originatingLegalEntityCode`, `routingId`, and `acceptedAt`.
- **Reject (`422`)**: a routing-failure (grant/currency/tenor invalid or hub institution closed to new
  business for a Subscription/Increase, or a missing hub counterparty account). **No hub-side order is created and no leg-B event is emitted** — the
  reject is HTTP-only. The client closes `Received → Rejected` from this response directly.

### Idempotency

The hub-side idempotency key is `(originatingLegalEntityCode, routingId)`, enforced by a partial
unique index. A leg-A retry that collides with an existing hub-side order resolves to the same
`ACCEPTED` response — no duplicate is created. `originatingLegalEntityCode` is transport-proven, so a
buggy/hostile client can only dedupe-collide with its own prior requests.

### Account trust

LODH trusts the CGEG-supplied `portfolioNumber` (resolved by the client's `ExternalIdentityGateway`)
and performs no account-existence or account-ownership check. Account failures surface at booking
time, not as a routing reject. No deployment-internal order UUID crosses the boundary.

## Thin-client reference-data reads

The client deployment stores **no hub-owned** reference data; it reads currencies, rates, and grants
**live from the hub** per request. It does store its own **onboarded institutions**, their counterparty
accounts, and its client enablement (client-owned facts, ADR 0008), each linked to a hub-native
institution code. These endpoints return **hub-native** institution codes; `/reference/institutions`
supplies the hub display names for the client's granted-institution list.
Grants are scoped automatically to the proven `originatingLegalEntityCode`; the client cannot claim
another client's grants. Every rate read (`/term-rates`, `/term-rates/latest`, `/oncall-segments`)
returns only rows whose `(institution, currency)` is covered by an active grant to the proven client;
tenor / notice-period permission is evaluated on the client through effective enablement. Rate
definitions are the hub's own: latest uploaded Term rate; OnCall segment status `VALID` or
`PENDING_CONFIRMATION`. The client reports a failed read of any of these as `503` on its own API.

## Error codes

`CrossOrgErrorResponse` (400/401/403): `VALIDATION_ERROR` (malformed request), `UNAUTHORIZED`
(missing/unresolvable credential), `FORBIDDEN` (membership failure — the credential resolved to a
legal entity that is not a member of this hub's TradingClient list; covers both the gateway
early-reject and the use-case membership re-check).

A routing-failure reject returns `422` with `RoutedOrderRejectResponse` (`outcome = REJECTED` +
`reason`, echoing the proven `originatingLegalEntityCode` + `routingId`), not the error envelope.
