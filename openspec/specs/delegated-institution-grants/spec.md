# delegated-institution-grants Specification

## Purpose

Trader-managed delegation of a TradingHub's native onboarded institutions to connected TradingClients per currency. A **delegated institution grant** authorises a TradingClient to intake orders against a hub institution through a **thin-proxy institution**. Defines the grant lifecycle, the DelegatedGrantDirectory out-port, hub-owned reference-data read semantics, and the contract-first grant CRUD surface. Exposed via contract-first REST under `contracts/006-delegated-institution-grants/openapi.yaml`.

## Requirements

### Requirement: TradingHub delegates an institution to a TradingClient per currency

A TradingHub SHALL delegate one of its native onboarded institutions to a connected TradingClient for a given currency by creating a **delegated institution grant** keyed by `(hubInstitutionCode, clientLegalEntityCode, currency)`. A grant SHALL carry an `enabledTenors` set (Term workspace) and an `enabledNoticePeriods` set (OnCall workspace). A grant SHALL be **independent of the hub's own intake enablement**: the hub MAY enable a tenor or notice period for a client while it is off for the hub's own desk, and MAY keep one on for its own desk while withholding it from a client. A grant SHALL reference a hub-native institution that is active; a grant SHALL NOT be created for an inactive or unknown hub institution. Only a user with the Trader role on the TradingHub SHALL create, update, or delete a grant.

#### Scenario: Hub grants an institution to a client for a currency

- **WHEN** a Trader on TradingHub `LOC` creates a grant for hub institution `BNP` to TradingClient `PAR` for `EUR` with `enabledTenors` `["1M","3M"]`
- **THEN** the grant is persisted keyed by `(BNP, PAR, EUR)` with those enabled tenors and an empty `enabledNoticePeriods`

#### Scenario: Grant is independent of hub intake enablement

- **WHEN** `LOC` has tenor `1M` disabled for its own desk on `EUR` but the grant to `PAR` includes `enabledTenors` `["1M"]`
- **THEN** the grant is accepted and `PAR` may intake `EUR`/`1M` orders routed to `LOC`

#### Scenario: Grant for an inactive hub institution rejected

- **WHEN** a Trader attempts to grant hub institution `OLD` that is inactive
- **THEN** the system rejects the grant creation and no grant is persisted

#### Scenario: Only a TradingHub Trader may manage grants

- **WHEN** a `ClientRepresentative` on `PAR` attempts to create or update a grant
- **THEN** the system rejects the request (authorisation error) and no grant is changed

---

### Requirement: Grant enabled tenor and notice period subsets are bounded by the hub managed currency

A grant's `enabledTenors` SHALL be a subset of the hub's managed-currency `enabledTenors` for that currency, and the grant's `enabledNoticePeriods` SHALL be a subset of the hub's managed-currency `enabledNoticePeriods` for that currency. A grant SHALL NOT enable a tenor or notice period that the hub has not enabled on the managed currency. The configurable tenor codes SHALL be `1W`, `2W`, `1M`, `3M`, `6M`, `1Y`; the configurable notice period codes SHALL be `24H`, `48H`.

#### Scenario: Grant tenor outside the hub managed set rejected

- **WHEN** `LOC`'s managed `EUR` has `enabledTenors` `["1M","3M"]` and a grant to `PAR` is created with `enabledTenors` `["1M","6M"]`
- **THEN** the system rejects the grant and no grant is persisted

#### Scenario: Grant notice period subset accepted

- **WHEN** `LOC`'s managed `EUR` has `enabledNoticePeriods` `["24H","48H"]` and a grant to `PAR` is created with `enabledNoticePeriods` `["24H"]`
- **THEN** the grant is persisted with `enabledNoticePeriods` `["24H"]`

---

### Requirement: Grants are prospective and do not rewrite history

Creating, updating, or deleting a grant SHALL NOT retroactively change already-routed or executed orders. Historical orders keep the grant (enabled set and active state) in effect at the moment they were routed. Grant changes affect only subsequent intake.

#### Scenario: Disabling a tenor on a grant does not affect routed orders

- **WHEN** a grant to `PAR` is updated to remove `3M` from `enabledTenors` after a `PAR` order for `EUR`/`3M` was already routed
- **THEN** the already-routed order is unchanged; only subsequent `PAR` intake for `EUR`/`3M` is rejected

---

### Requirement: Grant deactivation blocks new client intake but leaves in-flight orders untouched

A grant SHALL be deactivatable (active false) without hard-deletion. A deactivated grant SHALL block new TradingClient intake for its `(hubInstitution, client, currency)` and enabled set. Deactivation SHALL NOT cancel or alter already-`RECEIVED` or `ROUTED` client-side orders, nor the linked hub-side orders.

#### Scenario: Deactivated grant blocks new intake

- **WHEN** the grant `(BNP, PAR, EUR)` is deactivated and Portfolio Management submits a new `PAR` order for `BNP via LOC` in `EUR`
- **THEN** intake is rejected and no order is created

#### Scenario: Deactivated grant leaves in-flight orders untouched

- **WHEN** the grant `(BNP, PAR, EUR)` is deactivated while a `PAR` order for `EUR`/`3M` is already `ROUTED`
- **THEN** that routed order and its linked hub-side order remain unchanged

---

### Requirement: Thin-proxy institution references the hub native institution with a derived name

A TradingClient SHALL trade a granted institution through a **thin-proxy institution** that references the hub's native institution (`hubLegalEntityCode` + `hubInstitutionCode`) and whose `displayName` is derived deterministically as **"{hub institution displayName} via {hubLegalEntityCode}"** (e.g. `BNP via LOC`). A proxy SHALL be persisted as active, carry its own generated `institutionCode` for client-scope identity, and SHALL NOT hold its own term or OnCall rate curves — rates for the proxy are the hub's rates for the referenced native institution, read in-process. A proxy SHALL NOT be created unless an active grant exists for that `(hubInstitution, client)` for at least one currency.

#### Scenario: Proxy display name is derived

- **WHEN** `PAR` onboards a proxy for hub institution `BNP` (displayName `BNP`) whose hub is `LOC`
- **THEN** the proxy's `displayName` is `BNP via LOC` and the proxy references `(LOC, BNP)`

#### Scenario: Proxy carries no own rate curves

- **WHEN** the term and OnCall rates for `PAR`'s proxy `BNP via LOC` are queried
- **THEN** they equal the hub `LOC`'s rates for native institution `BNP` for the granted currencies

#### Scenario: Proxy without a grant rejected

- **WHEN** `PAR` attempts to onboard a proxy for `BNP` while no active grant `(BNP, PAR, *)` exists
- **THEN** the system rejects the proxy creation and no proxy is persisted

---

### Requirement: DelegatedGrantDirectory out-port

The system SHALL expose a `DelegatedGrantDirectory` out-port that, for a `(clientLegalEntityCode, proxyInstitutionCode, currency)` and a proposed tenor or notice period, resolves whether an active grant exists and whether the proposed tenor/notice is within the grant's enabled set. In V1 the port SHALL be backed by the `delegated_institution_grant` reference data. The port SHALL be the single seam consumed by TradingClient intake validation and order routing.

#### Scenario: Directory confirms a granted tenor

- **WHEN** the directory is queried for `(PAR, BNP-via-LOC, EUR)` with tenor `3M` and the grant `(BNP, PAR, EUR)` is active with `enabledTenors` including `3M`
- **THEN** the directory reports the tenor as granted

#### Scenario: Directory rejects a tenor outside the grant

- **WHEN** the directory is queried for `(PAR, BNP-via-LOC, EUR)` with tenor `6M` and the grant's `enabledTenors` is `["1M","3M"]`
- **THEN** the directory reports the tenor as not granted

#### Scenario: Directory rejects when no active grant exists

- **WHEN** the directory is queried for `(PAR, BNP-via-LOC, USD)` and no active grant `(BNP, PAR, USD)` exists
- **THEN** the directory reports no grant

---

### Requirement: Reference data is hub-owned and client-read-only

Managed currencies, term rates, and OnCall rate curves SHALL be owned and managed by the TradingHub LegalEntity. A TradingClient SHALL read its connected hub's reference data in-process (same deployment, ADR-0001) and SHALL NOT create, update, deactivate, or delete currencies, term rate uploads, or OnCall rate segments. A TradingClient's intake currency and rate validation SHALL use the connected hub's reference data.

#### Scenario: TradingClient reads the hub's currencies

- **WHEN** a `ClientRepresentative` on `PAR` requests the managed currency list
- **THEN** the system returns `LOC`'s managed currency catalog (read-only)

#### Scenario: TradingClient cannot mutate currencies

- **WHEN** a `ClientRepresentative` on `PAR` attempts to onboard or update a managed currency
- **THEN** the system rejects the request with an authorisation error and no currency is changed

#### Scenario: TradingClient cannot upload term rates or add OnCall rates

- **WHEN** a `ClientRepresentative` on `PAR` attempts a term rate upload or an OnCall rate add/cancel
- **THEN** the system rejects the request with an authorisation error and no rate data is changed

---

### Requirement: Grant CRUD is contract-first OpenAPI

The grant create, read, update, and deactivate surface SHALL be defined first in `contracts/006-delegated-institution-grants/openapi.yaml` with a prose mirror in `api-v1.md`, before controllers and generated interfaces are changed. Runtime request and response shapes SHALL match the published contract in the same delivery as behavioural changes. Grant CRUD SHALL be Trader-only (TradingHub).

#### Scenario: Grant endpoints documented in OpenAPI

- **WHEN** the grant CRUD contract is reviewed for this delivery
- **THEN** the operations, schemas, and error codes are declared in `contracts/006-delegated-institution-grants/openapi.yaml` and described in `api-v1.md`

#### Scenario: Implementation follows OpenAPI

- **WHEN** a TradingHub Trader calls the documented grant endpoints with valid payloads
- **THEN** server behaviour and response schemas match the canonical OpenAPI document
