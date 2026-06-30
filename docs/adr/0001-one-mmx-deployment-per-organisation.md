# One MMX deployment per Organisation, multi-tenant by LegalEntity

**Status:** accepted

MMX is deployed **one instance per Organisation** (e.g. one deployment for `LODH`, a separate one for `HSBC`), and all the LegalEntities of that Organisation (e.g. `LOC`, `PAR`, `SIN`) share the single deployment as tenants. We chose organisation-level isolation so a problem in one organisation cannot impact another, while avoiding the N× cost and cross-instance messaging of one deployment per LegalEntity. Order routing between a TradingClient and its TradingHub is therefore in-process (same deployment), and there is no cross-organisation routing in V1.

## Considered options

- **One deployment per LegalEntity** — strongest isolation, but requires inter-instance messaging for routing, N deployments, and duplicated reference data. Rejected: routing is only ever intra-organisation, so the isolation it buys is not worth the cost.
- **One global multi-tenant instance** — simplest, but no organisation-level fault isolation. Rejected: organisation-level blast-radius isolation is a hard requirement.
- **One deployment per Organisation (chosen).**

## Consequences

- LegalEntity is a tenant/partition dimension inside one deployment, not a deployment of its own.
- Identity, settings, and order data are scoped per LegalEntity within the shared deployment.
- Cross-organisation integration (if ever needed) is out of scope for V1 and would require a new inter-instance contract.
