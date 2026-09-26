# Spec Delta

## Purpose

Defines the Term and OnCall **counterparty accounts** that each LegalEntity holds per institution. It covers the accounts' shape, who maintains them, and the rule that no order is accepted on an institution that lacks the counterparty account for the order's OrderType. The Deposits back office books against these accounts.

## ADDED Requirements

### Requirement: Each LegalEntity holds one Term and one OnCall counterparty account per institution

Every institution owned by a LegalEntity SHALL carry at most one **Term counterparty account** and at most one **OnCall counterparty account**. This covers a TradingHub's native institution and a TradingClient's onboarded institution alike. A counterparty account SHALL be a non-blank, trimmed reference of at most 34 characters, with no currency dimension. Accounts SHALL belong to the owning LegalEntity: the same underlying bank at two LegalEntities (BNP at `LOC` and BNP at `PAR`) SHALL hold independent accounts. Both accounts SHALL be optional when the institution is created. Institution list and detail responses SHALL expose `termCounterpartyAccount` and `onCallCounterpartyAccount`, which are null when unset.

#### Scenario: Accounts are independent per LegalEntity

- **WHEN** `LOC`'s native `BNP` has Term counterparty account `LOC-BNP-T` and `PAR`'s onboarded `BNP via LOC` has Term counterparty account `PAR-BNP-T`
- **THEN** each institution's detail returns its own account, and changing one leaves the other unchanged

#### Scenario: Institution created without accounts

- **WHEN** a Trader on `LOC` onboards a native institution without supplying counterparty accounts
- **THEN** the institution is persisted with `termCounterpartyAccount` and `onCallCounterpartyAccount` null

#### Scenario: Blank account rejected

- **WHEN** a user sets `termCounterpartyAccount` to a whitespace-only value
- **THEN** the system rejects the request with a validation error and the stored account is unchanged

---

### Requirement: Counterparty accounts are maintained by the owning LegalEntity's settings role

On a TradingHub, a user with the **Trader** role SHALL set, change, or clear the counterparty accounts of the hub's native institutions. On a TradingClient, a user with the **ClientRepresentative** role SHALL set, change, or clear the counterparty accounts of the client's onboarded institutions. No user SHALL change the counterparty accounts of an institution owned by a LegalEntity other than their active scope. Setting accounts SHALL be allowed whether or not the institution is closed to new business. The operation SHALL be defined contract-first in `contracts/004-institution-settings/openapi.yaml` and mirrored in `api-v1.md`.

#### Scenario: Trader sets hub accounts

- **WHEN** a Trader on `LOC` sets `termCounterpartyAccount` `LOC-BNP-T` and `onCallCounterpartyAccount` `LOC-BNP-OC` on native `BNP`
- **THEN** both accounts are persisted and returned on the institution

#### Scenario: ClientRepresentative sets client accounts

- **WHEN** a ClientRepresentative on `PAR` sets `onCallCounterpartyAccount` `PAR-BNP-OC` on onboarded `BNP via LOC`
- **THEN** the account is persisted on `PAR`'s institution only

#### Scenario: Cross-scope account change rejected

- **WHEN** a ClientRepresentative on `PAR` attempts to change the accounts of `LOC`'s native `BNP`
- **THEN** the system rejects the request (not found in scope, or authorisation error) and no account changes

---

### Requirement: An order is refused when the counterparty account for its OrderType is missing

Intake SHALL reject an order when the order's institution, at the order's owning LegalEntity, has no counterparty account for the order's OrderType (Term → Term counterparty account; OnCall → OnCall counterparty account). This SHALL apply to all four OrderOperations (Subscription, Increase, Decrease, Redemption), because every one of them books against the account. For a **routed** order, the hub's own native institution SHALL also hold the counterparty account for the order's OrderType. If it does not, routing SHALL fail as a **routing failure**, the client-side order SHALL become `REJECTED` with a routing reason, and no hub-side order SHALL be created. Execute SHALL also reject when the account for the order's OrderType has been cleared since intake. The order then stays `ASSIGNED`.

#### Scenario: Hub intake without Term account rejected

- **WHEN** Portfolio Management submits a `LOC` Term order on native `BNP` and `BNP` at `LOC` has no Term counterparty account
- **THEN** intake is rejected with a clear counterparty-account error and no order is created

#### Scenario: Client intake without OnCall account rejected

- **WHEN** Portfolio Management submits a `PAR` OnCall Redemption on `BNP via LOC` and `PAR`'s `BNP via LOC` has no OnCall counterparty account
- **THEN** the client-side order is `REJECTED` with a clear counterparty-account reason and no hub-side order is created

#### Scenario: Missing hub account is a routing failure

- **WHEN** `PAR`'s `BNP via LOC` has a Term counterparty account, but `LOC`'s native `BNP` has none, and Portfolio Management submits a valid `PAR` Term order
- **THEN** the client-side order becomes `REJECTED` with a routing-failure reason and no hub-side order is created

#### Scenario: Account cleared after intake blocks execute

- **WHEN** an assigned `LOC` Term order's institution has its Term counterparty account cleared, and the trader executes
- **THEN** execute is rejected with a clear counterparty-account error and the order remains `ASSIGNED`

---

### Requirement: Client enablement requires the OrderType's counterparty account

A `ClientRepresentative` SHALL NOT enable a Term tenor on an onboarded institution that has no Term counterparty account, nor an OnCall notice period on one that has no OnCall counterparty account. Clearing a counterparty account SHALL be rejected while any tenor or notice period of that OrderType is client-enabled on that institution, for any currency. The ClientRepresentative switches those off first. The intake rule above remains as a backstop.

#### Scenario: Enabling a Term tenor without the Term account is rejected

- **WHEN** `PAR`'s `BNP via LOC` has no Term counterparty account and the ClientRepresentative enables `EUR`/`3M`
- **THEN** the request is rejected with a clear counterparty-account error and client enablement is unchanged

#### Scenario: Clearing an account in use is rejected

- **WHEN** `PAR` has client-enabled `EUR`/`24H` on `BNP via LOC` and the ClientRepresentative clears the OnCall counterparty account
- **THEN** the request is rejected and the account is unchanged

#### Scenario: Clearing an account after switching off succeeds

- **WHEN** `PAR` has switched off every OnCall notice period on `BNP via LOC` and clears the OnCall counterparty account
- **THEN** the account is cleared
