# Spec Delta

## MODIFIED Requirements

### Requirement: Grant deactivation blocks new client intake but leaves in-flight orders untouched

A grant SHALL be deactivatable (active false) without hard-deletion. A deactivated grant, or a tenor or notice period removed from a grant, SHALL make the affected `(hubInstitution, client, currency, tenor|noticePeriod)` **closed to new business**. The client's new **Subscription** and **Increase** intake for that scope SHALL be rejected. The client's **Decrease** and **Redemption** intake against an existing contract SHALL still be accepted and routed. Deactivation SHALL NOT cancel or alter already-`RECEIVED` or `ROUTED` client-side orders, nor the linked hub-side orders. Deactivation SHALL NOT offboard the client's onboarded institution.

#### Scenario: Deactivated grant blocks new intake

- **WHEN** the grant `(BNP, PAR, EUR)` is deactivated and Portfolio Management submits a new `PAR` Subscription for `BNP via LOC` in `EUR`
- **THEN** intake is rejected and no hub-side order is created

#### Scenario: Deactivated grant still accepts Redemption

- **WHEN** the grant `(BNP, PAR, EUR)` is deactivated and Portfolio Management submits a `PAR` OnCall Redemption for an existing `EUR` contract on `BNP via LOC`
- **THEN** the order is accepted and routed to `LOC`

#### Scenario: Deactivated grant leaves in-flight orders untouched

- **WHEN** the grant `(BNP, PAR, EUR)` is deactivated while a `PAR` order for `EUR`/`3M` is already `ROUTED`
- **THEN** that routed order and its linked hub-side order remain unchanged

#### Scenario: Deactivated grant keeps the onboarded institution

- **WHEN** every grant `(BNP, PAR, *)` is deactivated
- **THEN** `PAR`'s onboarded `BNP via LOC` still exists with its counterparty accounts and is not offboarded

---

### Requirement: DelegatedGrantDirectory out-port

The system SHALL expose a `DelegatedGrantDirectory` out-port. For a `(clientLegalEntityCode, onboardedInstitutionCode, currency)`, a proposed tenor or notice period, and an OrderOperation, it SHALL resolve whether the order is permitted. For **Subscription** and **Increase**, an active grant SHALL exist and the proposed tenor or notice period SHALL be within its enabled set. For **Decrease** and **Redemption**, the order SHALL be permitted regardless of the grant's current state. In V1 the port SHALL be backed by the `delegated_institution_grant` reference data for a local client, and by a live read of the hub's grants for a remote client. The port SHALL be the single seam consumed by TradingClient intake validation and order routing.

#### Scenario: Directory confirms a granted tenor

- **WHEN** the directory is queried for `(PAR, BNP-via-LOC, EUR)` Subscription with tenor `3M` and the grant `(BNP, PAR, EUR)` is active with `enabledTenors` including `3M`
- **THEN** the directory reports the order as permitted

#### Scenario: Directory rejects a tenor outside the grant

- **WHEN** the directory is queried for `(PAR, BNP-via-LOC, EUR)` Subscription with tenor `6M` and the grant's `enabledTenors` is `["1M","3M"]`
- **THEN** the directory reports the order as not permitted

#### Scenario: Directory rejects when no active grant exists

- **WHEN** the directory is queried for `(PAR, BNP-via-LOC, USD)` Subscription and no active grant `(BNP, PAR, USD)` exists
- **THEN** the directory reports no grant

#### Scenario: Directory permits Redemption on a revoked grant

- **WHEN** the directory is queried for `(PAR, BNP-via-LOC, EUR)` OnCall Redemption with notice `24H` and the grant `(BNP, PAR, EUR)` is inactive
- **THEN** the directory reports the order as permitted

## ADDED Requirements

### Requirement: A grant makes an institution granted, and onboarding makes it usable

An active delegated grant SHALL make the hub institution a **granted institution** for the client: eligible for **institution onboarding** (per `institution-onboarding`) but not usable at intake. A TradingClient SHALL trade a granted institution only through an **onboarded institution**. That record SHALL be owned by the client LegalEntity, permanently linked to the hub's native institution (`hubLegalEntityCode` + `hubInstitutionCode`), and SHALL carry a derived, non-editable `displayName` **"{hub institution displayName} via {hubLegalEntityCode}"** (e.g. `BNP via LOC`). It SHALL carry its own generated `institutionCode` and its own counterparty accounts (per `institution-counterparty-accounts`). It SHALL NOT hold its own term or OnCall rate curves: its rates SHALL be the hub's rates for the linked native institution.

#### Scenario: Granted but not onboarded is not usable

- **WHEN** grant `(BNP, PAR, EUR)` is active, `PAR` has not onboarded `BNP`, and Portfolio Management submits a `PAR` order on `BNP` in `EUR`
- **THEN** intake is rejected with a clear institution error

#### Scenario: Onboarded display name is derived

- **WHEN** `PAR` onboards hub institution `BNP` (displayName `BNP`) whose hub is `LOC`
- **THEN** the onboarded institution's `displayName` is `BNP via LOC` and it links to `(LOC, BNP)`

#### Scenario: Onboarded institution carries no own rate curves

- **WHEN** the term and OnCall rates for `PAR`'s `BNP via LOC` are queried
- **THEN** they equal the hub `LOC`'s rates for native institution `BNP` for the granted currencies

---

### Requirement: Client enablement is a client-controlled subset of the grant

A `ClientRepresentative` SHALL control the **client enablement** of each onboarded institution per `(onboarded institution, currency)`: the tenors (Term) and notice periods (OnCall) the client switches on for new business. The rules are:

- **Opt-in:** client enablement SHALL be empty right after onboarding.
- **Bounded by the grant:** a tenor or notice period SHALL be switched on only if the active grant for that currency enables it. A value already enabled MAY be kept in a replacement after the grant stops enabling it (it shows as "enabled, not granted" and is capped out of the effective enablement).
- **Kept when the grant shrinks:** a grant reduction or revocation SHALL NOT erase client enablement. The **effective enablement** used for new business SHALL always be *grant ∩ client enablement*, so restoring a grant restores the client's earlier choice.
- **Never widened by the grant:** a grant expansion SHALL NOT switch anything on.
- **Switching off:** switching off a tenor or notice period SHALL make that scope closed to new business (Subscription/Increase refused, Decrease/Redemption accepted).
- **Client-owned:** client enablement SHALL be stored in the client's own deployment (also for a remote client) and SHALL NOT be sent to the hub. The hub keeps validating only the grant.
- **Contract-first:** the operation SHALL be defined in `contracts/004-institution-settings/openapi.yaml`.

#### Scenario: Nothing is enabled after onboarding

- **WHEN** `PAR` onboards `BNP` while the grant `(BNP, PAR, EUR)` enables `["1M","3M"]`
- **THEN** `PAR`'s client enablement for `(BNP via LOC, EUR)` is empty, and a `PAR` Subscription for `EUR`/`1M` is rejected

#### Scenario: Enabling outside the grant is rejected

- **WHEN** the grant `(BNP, PAR, EUR)` enables `["1M","3M"]` and the ClientRepresentative enables `6M`
- **THEN** the request is rejected and client enablement is unchanged

#### Scenario: Grant reduction caps, grant restoration restores

- **WHEN** `PAR` has client-enabled `EUR`/`3M`, `LOC` removes `3M` from the grant, and later adds it back
- **THEN** a `PAR` `EUR`/`3M` Subscription is rejected while `3M` is not granted and accepted again once it is restored, without any client action

#### Scenario: Grant expansion does not enable anything

- **WHEN** `LOC` adds `6M` to the grant `(BNP, PAR, EUR)`
- **THEN** `PAR`'s client enablement is unchanged and a `PAR` `EUR`/`6M` Subscription is rejected until the ClientRepresentative enables `6M`

#### Scenario: Remote client enablement stays in the client deployment

- **WHEN** a ClientRepresentative on remote client `CGD` enables `EUR`/`3M` on `BNP via LOC`
- **THEN** the enablement is persisted in `CGD`'s deployment and no request is sent to the hub

#### Scenario: Trader cannot change client enablement

- **WHEN** a Trader on `LOC` attempts to change `PAR`'s client enablement
- **THEN** the system rejects the request with an authorisation error

## REMOVED Requirements

### Requirement: Thin-proxy institution references the hub native institution with a derived name

**Reason**: The thin proxy is retired. A client-owned **onboarded institution** replaces it, carrying counterparty accounts and onboarding state (ADR 0008).

**Migration**: Existing proxy rows are converted in place into onboarded institutions with empty counterparty accounts (same `institutionCode`, same hub link). See *A grant makes an institution granted, and onboarding makes it usable*.
