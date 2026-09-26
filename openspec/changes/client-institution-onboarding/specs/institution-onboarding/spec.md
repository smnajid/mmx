# Spec Delta

## MODIFIED Requirements

### Requirement: Deactivate and activate institutions

The system SHALL allow a Trader on a TradingHub to deactivate a native institution (`active` false) and later reactivate it (`active` true). The system MUST NOT hard-delete institution rows in this capability. Deactivating a hub institution SHALL make it **closed to new business** for the hub's own intake and for every TradingClient's routed intake (per `order-institution-constraints`). It SHALL NOT refuse Decrease or Redemption against existing contracts. Both deactivation and reactivation SHALL record an **institution export** for the hub LegalEntity.

#### Scenario: Deactivate active institution

- **WHEN** the trader deactivates `HSBC-01` that is active
- **THEN** the institution is persisted with `active` false and `closedToNewBusiness` true, returned with HTTP success, and an institution export is recorded

#### Scenario: Activate inactive institution

- **WHEN** the trader activates `HSBC-01` that is inactive
- **THEN** the institution is persisted with `active` true and `closedToNewBusiness` false, returned with HTTP success, and an institution export is recorded

---

### Requirement: Institution settings UI under Settings hub

Institution list and onboard flows SHALL live under `/settings/institutions` (and child routes) within the Settings hub. Screens SHALL use the same return-to-desk and visual patterns as currency settings (`DeskReturnService`, `settings-panel`, desk theme tokens per `currency-settings-ui`). The institution detail screen SHALL show and allow editing of the Term and OnCall counterparty accounts for the caller's own institution.

#### Scenario: List shows code and display name

- **WHEN** the trader opens the institution settings list and institutions exist
- **THEN** each row shows `institutionCode`, `displayName`, and an open / closed-to-new-business indication

#### Scenario: Onboard form has display name only

- **WHEN** the trader opens institution onboard
- **THEN** the form does not allow editing `institutionCode` and submits `displayName` (plus optional counterparty accounts)

#### Scenario: Onboard success shows generated code

- **WHEN** the trader successfully onboards an institution
- **THEN** the UI surfaces the returned `institutionCode` (list refresh or confirmation)

#### Scenario: Detail edits counterparty accounts

- **WHEN** a Trader opens `BNP`'s detail on `LOC` and saves `termCounterpartyAccount` `LOC-BNP-T`
- **THEN** the screen shows the saved account

## ADDED Requirements

### Requirement: List granted institutions for a TradingClient

The system SHALL expose an authenticated operation for a `ClientRepresentative` on a TradingClient to list its **granted institutions**. These are hub institutions for which the client holds at least one active delegated grant (any currency). Each entry SHALL include `hubLegalEntityCode`, `hubInstitutionCode`, the derived `displayName`, the granted currencies, and `onboardedInstitutionCode` with its `closedToNewBusiness` flag when the client has already onboarded it. For a remote client, the list SHALL be built from a live read of the hub's grants joined with the client's own onboarded institutions. The operation SHALL be defined contract-first in `contracts/004-institution-settings/openapi.yaml`.

#### Scenario: Granted but not onboarded

- **WHEN** `PAR` holds an active grant `(SG, PAR, USD)` and has not onboarded `SG`
- **THEN** the list includes `SG via LOC` with currencies `["USD"]` and no `onboardedInstitutionCode`

#### Scenario: Revoked grant drops the institution from the granted list

- **WHEN** every grant `(SG, PAR, *)` is deactivated
- **THEN** `SG` no longer appears in `PAR`'s granted-institution list

#### Scenario: Trader cannot list granted institutions

- **WHEN** a Trader on `LOC` calls the granted-institution list
- **THEN** the system rejects the request with an authorisation error

---

### Requirement: Offboard and re-onboard an onboarded institution

A `ClientRepresentative` SHALL **offboard** an onboarded institution of their TradingClient. The system SHALL NOT delete it: it SHALL mark it **closed to new business**, keep its counterparty accounts, and record an institution export. Offboarding an already-offboarded institution SHALL be an idempotent success. Offboarding SHALL NOT alter orders already `RECEIVED`, `ROUTED`, or `EXECUTED`. **Re-onboarding** SHALL reopen the same record (same `institutionCode`, same accounts) to new business and record an institution export. It SHALL require at least one active grant for the hub institution, as for first onboarding. A Trader SHALL NOT offboard or re-onboard a client institution.

#### Scenario: Offboard closes to new business

- **WHEN** the ClientRepresentative on `PAR` offboards `BNP via LOC`
- **THEN** the institution is persisted with `closedToNewBusiness` true, its accounts unchanged, and an institution export is recorded

#### Scenario: Re-onboard reopens the same record

- **WHEN** the ClientRepresentative on `PAR` onboards `BNP` again after offboarding it, while grant `(BNP, PAR, EUR)` is active
- **THEN** the same `institutionCode` is reopened with `closedToNewBusiness` false, its accounts intact, and an institution export is recorded

#### Scenario: Re-onboard without a grant rejected

- **WHEN** `PAR` offboarded `BNP via LOC` and no active grant `(BNP, PAR, *)` remains
- **THEN** re-onboarding is rejected and the institution stays closed to new business

---

### Requirement: List institutions in the caller's scope

The system SHALL expose an authenticated operation to list institutions in the catalog visible to the caller's active scope. Each list entry SHALL include `institutionCode`, `displayName`, `active`, `closedToNewBusiness`, `termCounterpartyAccount`, and `onCallCounterpartyAccount`. A **Trader** on a **TradingHub** SHALL see the hub's **native** institutions. A **ClientRepresentative** on a **TradingClient** SHALL see only that TradingClient's **onboarded institutions**, including offboarded ones, each flagged `closedToNewBusiness`. Each entry SHALL also carry `hubLegalEntityCode` and `hubInstitutionCode`. Native hub institutions SHALL NOT appear in a client's list. This holds for same-Organisation clients and for remote (cross-Organisation) clients alike.

#### Scenario: Empty catalog on cold start

- **WHEN** no institution has been onboarded and the user requests the institution list
- **THEN** the system returns an empty list with HTTP success

#### Scenario: Trader list returns native hub institutions

- **WHEN** `HSBC-01` and `BCI-01` exist as native institutions on hub `LOC` and a Trader on `LOC` requests the list
- **THEN** the list contains both native entries with `institutionCode`, `displayName`, `active`, `closedToNewBusiness`, and both counterparty accounts

#### Scenario: Client list returns only onboarded institutions

- **WHEN** `PAR` has onboarded `BNP via LOC` and a `ClientRepresentative` on `PAR` requests the list
- **THEN** the list contains `PAR`'s onboarded institutions only, each with its hub reference, and does not include `LOC`'s native institutions

#### Scenario: Remote client list is served from the client deployment

- **WHEN** a `ClientRepresentative` on remote client `CGD` has onboarded `BNP via LOC` and requests the list while the hub deployment is unreachable
- **THEN** the list is returned from `CGD`'s own deployment and contains `BNP via LOC`

---

### Requirement: Onboard a native or granted institution with system-generated institutionCode

The system SHALL allow an authorised user to onboard an institution by supplying **`displayName`** only (non-blank, trimmed). The system MUST NOT accept `institutionCode` from the client on create. On success, the system SHALL generate a unique immutable **`institutionCode`** in the form **`{ACRONYM}-{nn}`** (e.g. `HSBC-01`, `BCI-02`), persist the institution as **active**, and return the full entry including the generated code.

**Acronym rules:** single word 2–6 alphanumeric characters → uppercased token as acronym; multiple words → first alphanumeric character of each word, uppercased, max 6 characters; if acronym would be empty → base `INST`. **Suffix:** for a given acronym base, assign the next two-digit suffix `01`–`99` among existing codes with that base; reject onboard if suffix would exceed `99`.

**Role-qualified onboarding (per `delegated-institution-grants`):**

- **Trader.** A user with the **Trader** role on a **TradingHub** SHALL onboard a **native** institution by supplying `displayName` as above, and optionally its counterparty accounts.
- **ClientRepresentative.** A user with the **ClientRepresentative** role on a **TradingClient** SHALL perform **institution onboarding** by selecting a **granted institution** (a hub institution for which the client holds at least one active grant for any currency), identified by `hubInstitutionCode`, and optionally its counterparty accounts. The system SHALL:
  - derive the `displayName` as "{hub institution displayName} via {hubLegalEntityCode}";
  - generate the `institutionCode` by applying the acronym rules to that derived name;
  - persist an **onboarded institution** owned by the client LegalEntity and permanently linked to `(hubLegalEntityCode, hubInstitutionCode)`.

  A ClientRepresentative SHALL NOT supply a free-form `displayName`.
- **At most one per hub institution.** A TradingClient SHALL hold at most one onboarded institution per hub institution. Onboarding a hub institution that the client has already onboarded and then offboarded SHALL **re-onboard** the existing record (see *Offboard and re-onboard*), not create a second one.
- **Remote clients.** For a remote (cross-Organisation) client, the granted-institution check SHALL use a live read of the hub's grants, and the onboarded institution SHALL be persisted in the client's own deployment.
- **Export.** Every successful onboarding SHALL record an **institution export** (per `back-office-outbound-messaging`).

#### Scenario: Successful onboard single-word name

- **WHEN** a TradingHub Trader submits `displayName` `HSBC` and no institution with base `HSBC` exists
- **THEN** the system persists `institutionCode` `HSBC-01`, `active` true, and returns HTTP 201 with the entry

#### Scenario: Second institution same acronym base

- **WHEN** `HSBC-01` already exists and a TradingHub Trader submits `displayName` `HSBC` again
- **THEN** the system persists `institutionCode` `HSBC-02` and returns HTTP 201

#### Scenario: Multi-word display name derives acronym

- **WHEN** a TradingHub Trader submits `displayName` `Bank Co International` and no `BCI-%` codes exist
- **THEN** the system persists `institutionCode` `BCI-01`

#### Scenario: Reject blank display name

- **WHEN** a TradingHub Trader submits an empty or whitespace-only `displayName`
- **THEN** the system rejects the request with a clear validation error and does not persist

#### Scenario: Reject suffix overflow for acronym base

- **WHEN** institutions `INST-01` through `INST-99` already exist and another onboard derives acronym base `INST`
- **THEN** the system rejects onboard with a clear error and does not persist

#### Scenario: ClientRepresentative onboards a granted institution

- **WHEN** a `ClientRepresentative` on `PAR` holds an active grant `(BNP, PAR, EUR)` and onboards hub institution `BNP` (displayName `BNP`) whose hub is `LOC`
- **THEN** the system persists an onboarded institution owned by `PAR` with derived `displayName` `BNP via LOC`, a generated `institutionCode`, and a link to `(LOC, BNP)`, and records an institution export

#### Scenario: Remote ClientRepresentative onboards a granted institution

- **WHEN** a `ClientRepresentative` on remote client `CGD` holds an active grant `(BNP, CGD, EUR)` at hub `LOC` and onboards `BNP`
- **THEN** the onboarded institution `BNP via LOC` is persisted in `CGD`'s deployment, linked to `(LOC, BNP)`

#### Scenario: Onboarding a non-granted institution rejected

- **WHEN** a `ClientRepresentative` on `PAR` attempts to onboard `BNP` while no active grant `(BNP, PAR, *)` exists
- **THEN** the system rejects the request and no institution is persisted

#### Scenario: ClientRepresentative cannot supply a free-form name

- **WHEN** a `ClientRepresentative` onboards a granted institution and submits a free-form `displayName`
- **THEN** the system rejects the request and the `displayName` is derived only from the hub institution

#### Scenario: Duplicate onboarding rejected

- **WHEN** `PAR` already holds an open (not offboarded) onboarded institution for `(LOC, BNP)` and the ClientRepresentative onboards `BNP` again
- **THEN** the system rejects the request with a conflict error and no second institution is created

---

### Requirement: ClientRepresentative onboarded-institution settings surface

A `ClientRepresentative` on a TradingClient SHALL access institutions under `/settings/institutions` (Settings only, per `trader-desk-navigation` role gating). The client institution screen SHALL:

- list the client's **onboarded institutions**, each with an open / closed-to-new-business indication and its counterparty accounts;
- offer an onboard flow that lists the **granted institutions** not yet open for the client;
- allow offboarding and re-onboarding;
- allow editing the counterparty accounts.

Per currency, the screen SHALL let the ClientRepresentative switch the **client enablement** on or off for each tenor (Term) and notice period (OnCall) the grant makes available (per `delegated-institution-grants`). Tenors outside the grant SHALL be disabled. A tenor that is client-enabled but no longer granted SHALL be shown as "enabled, not granted". Currencies, Term rates, and OnCall rates screens SHALL render read-only for a `ClientRepresentative` (per `delegated-institution-grants` reference-data ownership). The word "proxy" SHALL NOT appear on client screens.

#### Scenario: Client sees onboarded institutions and grant bounds

- **WHEN** a `ClientRepresentative` on `PAR` opens `/settings/institutions`, has onboarded `BNP via LOC`, and holds grant `(BNP, PAR, EUR)` with `enabledTenors` `["1M","3M"]`
- **THEN** the screen lists `BNP via LOC` and, for `EUR`, offers `1M` and `3M` as switchable while other tenors are disabled

#### Scenario: Enabled but no longer granted is flagged

- **WHEN** `PAR` has client-enabled `EUR`/`3M` on `BNP via LOC` and `LOC` removes `3M` from the grant
- **THEN** the screen shows `3M` as "enabled, not granted"

#### Scenario: Onboard flow lists only granted, not-yet-open institutions

- **WHEN** `PAR` holds active grants for `BNP` and `SG` and has already onboarded `BNP`
- **THEN** the onboard flow offers `SG` and does not offer `BNP`

#### Scenario: Client currency and rate screens are read-only

- **WHEN** a `ClientRepresentative` on `PAR` opens the Currencies, Term rates, or OnCall rates settings screens
- **THEN** the screens render read-only with no mutate controls

## REMOVED Requirements

### Requirement: List onboarded institutions

**Reason**: The client list no longer contains thin proxies; it lists onboarded institutions and exposes counterparty accounts and the closed-to-new-business flag.

**Migration**: Superseded by *List institutions in the caller's scope*; proxies were converted in place into onboarded institutions.

### Requirement: Onboard an institution with system-generated institutionCode

**Reason**: Client proxy onboarding is replaced by institution onboarding from a granted institution, including remote clients and re-onboarding.

**Migration**: Superseded by *Onboard a native or granted institution with system-generated institutionCode*; the generation rules are unchanged.

### Requirement: ClientRepresentative institution settings surface

**Reason**: The client screen now lists onboarded institutions with counterparty accounts and offers onboard/offboard flows; proxy wording is removed.

**Migration**: Superseded by *ClientRepresentative onboarded-institution settings surface*.
