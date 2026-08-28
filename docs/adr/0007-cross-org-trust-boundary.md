# Cross-org trust boundary: transport-proven identity, trust authoritative resolution, validate only the grant

**Status:** accepted

When a TradingClient in one Organisation (e.g. CGD@CGED) routes an order to a TradingHub in another Organisation (e.g. LOC@LODH), the two deployments communicate over REST and Kafka. Neither deployment has direct database access to the other's reference data. We must decide what each side trusts, what it validates, and where the trust boundary sits.

We decided on three principles:

1. **Transport-proven identity.** The client deployment authenticates to the hub deployment's REST endpoint using provisioned credentials (`mmx.cross-org.credentials`). The hub trusts that the authenticated caller is the provisioned client Organisation. No internal MMX user identity crosses the boundary — the REST request carries only domain fields (routing id, legal entity codes, institution code, amounts).

2. **Trust authoritative resolution.** The client deployment resolves the hub-side portfolio number (global account) via `ExternalIdentityGateway` *before* sending Leg A. The hub trusts this resolved account — it does NOT re-resolve it. Rationale: the hub-side account directory is the authoritative source; the client called the hub's identity endpoint to get it; re-validating it at intake would be redundant. The hub validates only the **delegated grant** (is this institution granted to this client for this currency/tenor?) — because the grant is the hub's own reference data and the authority for accepting the order.

3. **No foreign LegalEntity is stored.** The client deployment does not persist the hub's LegalEntity in its own database. The hub connection (connected hub code, organisation code, endpoint URL, credentials) is provisioned via configuration (`mmx.cross-org.*` properties), not via the `LegalEntityRepository`. The `HubLocalityResolver` derives locality from the deployment's own OrganisationCode vs the connected hub's OrganisationCode — never from a stored entity.

## Considered options

- **Validate everything at the hub (don't trust the client)** — rejected: the hub-side order arrives via routing, not via the hub's own PM intake. The hub's intake enablement (managed currencies, institution active flags) does not apply to routed orders — the **grant itself is the authority** (see CONTEXT.md "Grant vs hub own-intake enablement"). Re-resolving the global account would duplicate the `ExternalIdentityGateway` call inside the hub's intake path and add latency without correctness gain.

- **Replicate reference data across deployments** — rejected: replicating institutions, currencies, and accounts between deployments creates a consistency problem (eventual consistency on reference data = wrong orders), violates the deployment-boundary isolation principle (ADR 0001), and adds complex sync infrastructure. The remote-backed adapter pattern (`RemoteManagedCurrencyRepository`, `RemoteInstitutionRepository`) lets the client deployment call the hub's REST endpoints read-only at runtime instead.

- **Store the foreign hub LegalEntity locally** — rejected: storing a LegalEntity that belongs to another Organisation blurs the tenancy model (each deployment owns only its Organisation's LegalEntities). The connection metadata lives in configuration; the `LegalEntityRepository` remains Organisation-scoped.

## Consequences

- The hub's `AcceptRoutedHubOrderUseCase` validates the **grant** (institution, client, currency, tenor/noticePeriod) and the **currency managed-ness**, but trusts the client-resolved global account and the client's institution code mapping. A routing-failure reject is returned synchronously (HTTP 422) if the grant is invalid.
- The client deployment must provision the hub connection once and keep credentials/endpoints in sync with the hub deployment. This is an operational concern (task 11.2 — connection-registration).
- `ExternalIdentityGateway` is a client-side port; it calls the hub's identity endpoint. If the hub's identity endpoint is down, the client cannot resolve the account, and Leg A cannot be sent. This is a transport-level failure — silence is never terminal (ADR 0006) applies: the order stays `Received`.
- The `CrossOrgMembershipPort` (hub-side) verifies that the originating client LegalEntity is in the hub's TradingClient membership list — this is the hub's own data, not the client's claim, and is the one identity check the hub performs beyond transport authentication.
