## MODIFIED Requirements

### Requirement: Thin remote client reads hub reference data live

For a remote TradingClient, the client deployment SHALL store **zero** hub reference data (no managed currencies, proxy institutions, delegated grants, or term/on-call rates). The client SHALL read currencies, rates, grants, and counterparties **live from the hub deployment** per request via remote-backed adapters over the existing read ports, selected when the connected hub is remote. The hub deployment's in-process grant check at leg-A accept SHALL be the **one true validation**; the client performs no authoritative pre-validation. Delegated grants for a remote client SHALL be mastered and stored in the hub deployment, keyed by the client's `LegalEntityCode`. Proxy indirection SHALL collapse for remote clients: the hub's live read returns hub-native institution codes, and the client renders the `"via {hub}"` display name client-side; the leg-A request carries the hub-native institution code.

The hub deployment SHALL serve, to a transport-proven client, the rate reads order creation needs: the **latest Term rate per institution** for a `(currency, tenor)`, the **open OnCall segments** (`VALID` or `PENDING_CONFIRMATION`) for a `(currency, noticePeriod)`, and the **OnCall segments covering a `valueDate`** for a `(currency, noticePeriod)`. Every cross-org rate read — these and the Term rate sheet for a trading date — SHALL return only rows whose `(institution, currency)` is covered by an active delegated grant to the proven client. Rate definitions SHALL be the hub's own (latest uploaded Term rate; OnCall segment status `VALID` or `PENDING_CONFIRMATION`).

A failed remote read (hub unreachable, timeout, credential rejected, or any non-success response) SHALL be reported by the client deployment as unavailable (`503 Service Unavailable` on its own API), never as an empty result.

#### Scenario: A remote client reads reference data live from the hub

- **WHEN** a CGD Portfolio Manager opens the order-creation form
- **THEN** the form is populated by live reads from LODH (currencies, counterparties, rates), and CGEG stores none of that reference data locally

#### Scenario: The hub validates the grant at leg-A accept

- **WHEN** `AcceptRoutedHubOrderUseCase` at LODH receives a leg-A request for CGD
- **THEN** it validates `(institution, currency, tenor|noticePeriod)` against CGD's grant using LODH's own reference data, and a grant violation rejects the request

#### Scenario: Hub-native institution code crosses the boundary

- **WHEN** a CGD order form offers counterparty "BNP via LOC"
- **THEN** the leg-A request carries LOC's hub-native institution code `BNP`, and LODH performs no proxy resolution

#### Scenario: Hub rate reads are scoped to the proven client's grants

- **WHEN** LODH has EUR/3M Term rates for BNP and SGFR and CGD holds an active grant only for `(BNP, EUR)`
- **THEN** a cross-org latest-Term-rate read for EUR/3M authenticated as CGD returns BNP's rate only

#### Scenario: Term rate sheet is scoped to the proven client's grants

- **WHEN** LODH's rate sheet for 2026-10-02 holds rows for BNP and SGFR in EUR and CGD holds an active grant only for `(BNP, EUR)`
- **THEN** `GET /api/v1/cross-org/reference/term-rates?tradingDate=2026-10-02` authenticated as CGD returns only the BNP EUR rows

#### Scenario: Hub OnCall segments are served to the remote client

- **WHEN** LODH's BNP has a PENDING_CONFIRMATION open segment for EUR/24H and CGD holds an active grant for `(BNP, EUR)`
- **THEN** the cross-org open-OnCall-segments read for EUR/24H authenticated as CGD returns that segment

#### Scenario: Unreachable hub is not an empty answer

- **WHEN** CGEG's read of LODH's currencies, institutions, grants or rates times out
- **THEN** the CGEG endpoint that needed it returns `503 Service Unavailable` instead of `200` with an empty list
