# user-identity-and-scoping Specification

## Purpose

Umbrella **MMXUser** identity resolved from the `X-User-Id` header (replacing legacy `X-Trader-Id`), multi-scope `(LegalEntityCode, Role)` bindings, single active session scope with re-scope, and role-based access gating desk vs settings.

## Requirements

### Requirement: Umbrella MMXUser identity with X-User-Id

Every human who logs into MMX SHALL be an **MMXUser** identified on trader/settings requests by the **`X-User-Id`** header (replacing the legacy `X-Trader-Id`). The system SHALL NOT accept `X-Trader-Id` as the identity header on trader/settings endpoints after this change.

#### Scenario: Request authenticated by X-User-Id

- **WHEN** a trader/settings request arrives with header `X-User-Id` identifying a known MMXUser
- **THEN** the system resolves the user and proceeds with authorisation

#### Scenario: Legacy X-Trader-Id rejected

- **WHEN** a trader/settings request arrives with `X-Trader-Id` and no `X-User-Id`
- **THEN** the system rejects the request as unauthenticated

---

### Requirement: MMXUser holds multiple (LegalEntity, role) scopes

An MMXUser SHALL hold zero or more allowed **scopes**, each a `(LegalEntityCode, Role)` pair. One human MAY hold scopes on more than one LegalEntity (e.g. a ClientRepresentative on both `PAR` and `SIN`). V1 roles SHALL be **Trader** and **ClientRepresentative**.

#### Scenario: User with multiple entity scopes

- **WHEN** an MMXUser holds scopes `(PAR, ClientRepresentative)` and `(SIN, ClientRepresentative)`
- **THEN** both scopes are persisted and available for session binding

#### Scenario: User with no scopes cannot act

- **WHEN** an MMXUser with no allowed scopes attempts any trader/settings operation
- **THEN** the system rejects the request as unauthorised

---

### Requirement: Session bound to one active scope, switchable by re-scope

A session SHALL be bound to exactly one **active scope** `(LegalEntityCode, Role)`, chosen at login from the user's allowed scopes. The server SHALL NOT accept a free per-request entity choice on trader/settings endpoints. A user with multiple scopes MAY switch the active scope via a re-scope operation; the system SHALL re-validate that the user holds the requested `(LegalEntityCode, Role)` and rebind the session. Re-scope is not a full re-authentication.

#### Scenario: Login binds the chosen scope

- **WHEN** an MMXUser with scopes `(PAR, ClientRepresentative)` and `(SIN, ClientRepresentative)` logs in and selects `PAR`
- **THEN** the session's active scope is `(PAR, ClientRepresentative)`

#### Scenario: Free per-request entity choice rejected

- **WHEN** a trader/settings request attempts to specify a LegalEntity via a request field other than the session
- **THEN** the system rejects the request

#### Scenario: Re-scope to a held scope succeeds

- **WHEN** the active scope is `(PAR, ClientRepresentative)` and the user re-scopes to `(SIN, ClientRepresentative)`, which they hold
- **THEN** the session's active scope becomes `(SIN, ClientRepresentative)`

#### Scenario: Re-scope to an unheld scope rejected

- **WHEN** the user re-scopes to `(LOC, Trader)`, which they do not hold
- **THEN** the system rejects the re-scope and the active scope is unchanged

---

### Requirement: Role-based access — desk for Traders, settings for ClientRepresentatives

A session whose active role is **Trader** (only meaningful on a TradingHub) MAY access Desk operations. A session whose active role is **ClientRepresentative** (a TradingClient) SHALL access Settings only and SHALL NOT access any Desk route or mutating desk operation.

#### Scenario: Trader accesses desk

- **WHEN** a session with active role `Trader` on a TradingHub requests a desk queue
- **THEN** the system serves the desk queue

#### Scenario: ClientRepresentative blocked from desk

- **WHEN** a session with active role `ClientRepresentative` requests a desk route or desk mutation
- **THEN** the system rejects the request

---

### Requirement: Portfolio Management intake is not session-scoped

Portfolio Management is a system caller scoped per Organisation, not an MMXUser session. The order's `LegalEntityCode` SHALL be supplied as a required field on the intake request, not derived from a session. (Modelled in `money-market-order-lifecycle`.)

#### Scenario: Intake uses body field, not session

- **WHEN** PM submits an order to `POST /api/v1/orders`
- **THEN** the order's LegalEntity is taken from the request's `legalEntityCode` field, not from a trader/settings session
