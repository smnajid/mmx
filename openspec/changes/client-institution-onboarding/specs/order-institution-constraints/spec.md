# Spec Delta

## MODIFIED Requirements

### Requirement: Execute requires active onboarded institutionCode

On execute, the trader request SHALL include only **`executedRate`**. The **`institutionCode`** is already set on the order at intake by Portfolio Management. The system SHALL use the intake-provided institution for execution; the trader MUST NOT change the counterparty. If the trader cannot deal with the PM-chosen counterparty, they MUST reject the order. At execution time the system MUST still validate that:

- the institution is not **closed to new business** when the order is a **Subscription** or **Increase**. A **Decrease** or **Redemption** SHALL be executable on an institution that is closed to new business;
- the institution holds the counterparty account for the order's OrderType (per `institution-counterparty-accounts`).

#### Scenario: Successful execute uses intake institution

- **WHEN** the assigned trader executes an order that was received with `institutionCode` `HSBC-01` (active, displayName `HSBC`, with a counterparty account for the order's OrderType) and provides only `executedRate` 3.45
- **THEN** the order transitions to EXECUTED with counterparty `HSBC` and executedRate 3.45

#### Scenario: Execute below minimumRate rejected

- **WHEN** the order has `minimumRate` 3.50 from intake and the assignee submits `executedRate` 3.45
- **THEN** the system rejects execute and the order remains ASSIGNED

#### Scenario: Execute with stale institution rejects

- **WHEN** institution `HSBC-01` was active at intake of a Subscription but is now deactivated, and the assigned trader submits execute
- **THEN** the system rejects execute with a clear institution error
- **AND** the order remains ASSIGNED

#### Scenario: Execute Redemption on a closed institution succeeds

- **WHEN** institution `HSBC-01` is deactivated and the assigned trader executes an OnCall Redemption on it
- **THEN** the order transitions to EXECUTED

#### Scenario: Trader rejects order when counterparty is unworkable

- **WHEN** the assigned trader cannot deal with the PM-chosen counterparty
- **THEN** the trader rejects the order with a reason explaining why the counterparty cannot be serviced

---

### Requirement: Intake unchanged for institutions in phase 1

**Superseded.** Portfolio Management intake (`POST /api/v1/orders`) SHALL require **`institutionCode`** referencing an institution owned by the order's LegalEntity. The system SHALL:

- validate the institution;
- derive `counterparty` from `Institution.displayName`;
- persist both on the order at reception.

`desiredCounterpartyComment` is removed from the intake request.

**Closed to new business:** a **Subscription** or **Increase** SHALL be rejected when the institution is closed to new business. At a TradingHub that means a deactivated native institution; at a TradingClient, an offboarded onboarded institution or a deactivated hub institution. A **Decrease** or **Redemption** SHALL NOT be rejected for that reason.

**TradingClient intake is grant-bound (per `delegated-institution-grants`):** when the order's owning LegalEntity is a TradingClient:

- the `institutionCode` SHALL reference an **onboarded institution** of that TradingClient;
- for a Subscription or Increase, a **delegated institution grant** for `(hubInstitution, clientLegalEntity, currency)` SHALL exist and be active, and the order's tenor (Term) or notice period (OnCall) SHALL be within the **effective enablement** for that currency: the grant's enabled set ∩ the client's own **client enablement**.

If any of these fail, intake SHALL be rejected, and the client-side order SHALL be `REJECTED` with no hub-side order created. Intake SHALL also enforce the counterparty-account rule of `institution-counterparty-accounts`.

#### Scenario: Intake with valid active institutionCode succeeds

- **WHEN** Portfolio Management submits a valid order with `institutionCode` `BNKCO` for active institution with displayName `BankCo`, which holds the counterparty account for the order's OrderType
- **THEN** intake succeeds, the order is persisted with institutionCode `BNKCO` and counterparty `BankCo`

#### Scenario: Intake with unknown institutionCode is rejected

- **WHEN** Portfolio Management submits an order with `institutionCode` `NOPE-01` that does not exist
- **THEN** the system rejects intake with a clear institution error
- **AND** no order is created

#### Scenario: Intake with inactive institutionCode is rejected

- **WHEN** Portfolio Management submits a Subscription with `institutionCode` `DEAD-01` for a deactivated institution
- **THEN** the system rejects intake with a clear institution error
- **AND** no order is created

#### Scenario: Redemption on an inactive institution is accepted

- **WHEN** Portfolio Management submits an OnCall Redemption with `institutionCode` `DEAD-01` against an existing contract on that deactivated institution
- **THEN** intake succeeds

#### Scenario: Intake without institutionCode is rejected

- **WHEN** Portfolio Management submits an order without `institutionCode`
- **THEN** the system rejects intake with a validation error

#### Scenario: TradingClient intake with granted tenor succeeds

- **WHEN** Portfolio Management submits a `PAR` Subscription for onboarded `BNP via LOC` in `EUR` with tenor `3M`, and the grant `(BNP, PAR, EUR)` is active with `enabledTenors` including `3M`
- **THEN** intake succeeds and the order is persisted with counterparty `BNP via LOC`

#### Scenario: TradingClient intake with tenor outside the grant is rejected

- **WHEN** Portfolio Management submits a `PAR` Subscription for `BNP via LOC` in `EUR` with tenor `6M`, and the grant's `enabledTenors` is `["1M","3M"]`
- **THEN** the system rejects intake with a clear grant error and no hub-side order is created

#### Scenario: TradingClient intake for a non-granted institution is rejected

- **WHEN** Portfolio Management submits a `PAR` Subscription on onboarded `SG via LOC` while no active grant `(SG, PAR, currency)` exists
- **THEN** the system rejects intake with a clear grant error and no hub-side order is created

#### Scenario: TradingClient intake for a granted but not onboarded institution is rejected

- **WHEN** Portfolio Management submits a `PAR` order on hub institution `SG` that `PAR` holds a grant for but has not onboarded
- **THEN** the system rejects intake with a clear institution error and no hub-side order is created

#### Scenario: TradingClient intake with a deactivated grant is rejected

- **WHEN** Portfolio Management submits a `PAR` Subscription for `BNP via LOC` in `EUR` and the grant `(BNP, PAR, EUR)` is inactive
- **THEN** the system rejects intake and no hub-side order is created

#### Scenario: TradingClient Subscription on a tenor the client has not enabled is rejected

- **WHEN** the grant `(BNP, PAR, EUR)` enables `["1M","3M"]`, `PAR` has client-enabled only `1M`, and Portfolio Management submits a `PAR` Subscription for `EUR`/`3M` on `BNP via LOC`
- **THEN** the system rejects intake with a clear enablement error and no hub-side order is created

#### Scenario: TradingClient Redemption on a notice period switched off by the client is accepted

- **WHEN** `PAR` has switched off `EUR`/`48H` on `BNP via LOC` and Portfolio Management submits a `PAR` OnCall Redemption against an existing `EUR`/`48H` contract
- **THEN** intake succeeds and the order is routed to `LOC`

#### Scenario: TradingClient Increase on an offboarded institution is rejected

- **WHEN** `PAR` has offboarded `BNP via LOC` and Portfolio Management submits a `PAR` OnCall Increase on it
- **THEN** the system rejects intake with a clear closed-to-new-business error and no hub-side order is created

#### Scenario: TradingClient Decrease on an offboarded institution is accepted

- **WHEN** `PAR` has offboarded `BNP via LOC` and Portfolio Management submits a `PAR` OnCall Decrease against an existing contract on it
- **THEN** intake succeeds and the order is routed to `LOC`

---

### Requirement: Lifecycle intake institution must match source contract

For OnCall orders with `orderOperation` ∈ {INCREASE, DECREASE, REDEMPTION} and a non-null `sourceContractNumber`, the system SHALL look up the original executed Subscription order by `generatedContractNumber` equal to `sourceContractNumber`. The submitted `institutionCode` MUST equal the subscription order's persisted `institutionCode`. If they differ, intake SHALL be rejected with a clear error and no order SHALL be created. This requirement applies at **both TradingHub intake and TradingClient (routed) intake**; the routing path SHALL NOT bypass it. On the routed path, the submitted `institutionCode` is the TradingClient's onboarded institution, and the lookup matches it against the source subscription's persisted onboarded `institutionCode`. Proxies converted in place keep their `institutionCode`, so historical subscriptions still match.

#### Scenario: Lifecycle intake with matching institution succeeds

- **WHEN** Portfolio Management submits an OnCall INCREASE with sourceContractNumber CT-00042 and institutionCode BNKCO, and the executed Subscription for CT-00042 has institutionCode BNKCO
- **THEN** intake succeeds

#### Scenario: Lifecycle intake with mismatched institution is rejected

- **WHEN** Portfolio Management submits an OnCall INCREASE with sourceContractNumber CT-00042 and institutionCode SGFR, but the executed Subscription for CT-00042 has institutionCode BNKCO
- **THEN** the system rejects intake with a clear institution/contract mismatch error
- **AND** no order is created

#### Scenario: Routed TradingClient lifecycle intake with mismatched institution is rejected

- **WHEN** Portfolio Management submits an OnCall INCREASE for TradingClient `PAR` with sourceContractNumber CT-00042 and onboarded institutionCode `BNP-VIA-LOC`, but the executed Subscription for CT-00042 has onboarded institutionCode `SGFR-VIA-LOC`
- **THEN** the system rejects intake with a clear institution/contract mismatch error
- **AND** no order is created (no client-side order, no hub-side order)

#### Scenario: Subscription intake is not subject to contract institution match

- **WHEN** Portfolio Management submits an OnCall SUBSCRIPTION with any open institutionCode and no sourceContractNumber
- **THEN** the lifecycle contract institution rule does not apply (normal institution validation only)
