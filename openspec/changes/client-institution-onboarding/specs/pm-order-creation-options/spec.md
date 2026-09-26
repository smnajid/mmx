# Spec Delta

## ADDED Requirements

### Requirement: Term counterparties open to new business with latest rates sorted by best rate

`GET /api/v1/order-creation/term/counterparties?legalEntityCode={legalEntityCode}&currency={currency}&tenor={tenor}` SHALL return institutions with term rates for the given currency and tenor, scoped by `legalEntityCode`. This list feeds new business, so it SHALL include only institutions that are open to new business and hold a Term counterparty account:

- **TradingHub:** active hub-native institutions that have at least one term rate and a Term counterparty account.
- **TradingClient:** only the client's **onboarded institutions** that are not offboarded, hold a Term counterparty account, and whose **effective enablement** (active grant ∩ client enablement) includes the currency and tenor. Their rates SHALL be sourced from the connected hub's linked native institution. The linked hub institution SHALL also hold a Term counterparty account.

For each institution, the system SHALL return the **latest** available rate (most recent trading date). Each entry SHALL include `institutionCode`, `displayName`, `rate`, `rateDate` (the trading date of the rate), and `indicative` (true when `rateDate` is before today). Results SHALL be sorted by rate descending (best rate first).

#### Scenario: Counterparty with today's rate is not indicative

- **WHEN** institution BNKCO has a term rate of 3.45 for EUR/3M uploaded today (2026-06-06)
- **THEN** the response includes BNKCO with rate 3.45, rateDate 2026-06-06, indicative false

#### Scenario: Counterparty with stale rate is indicative

- **WHEN** institution CDNRD has a term rate of 3.40 for EUR/3M from 2026-06-05 and no rate for 2026-06-06
- **THEN** the response includes CDNRD with rate 3.40, rateDate 2026-06-05, indicative true

#### Scenario: Counterparties sorted by best rate

- **WHEN** BNKCO offers 3.45 and CDNRD offers 3.40 for EUR/3M
- **THEN** BNKCO appears before CDNRD in the response

#### Scenario: Inactive institution excluded

- **WHEN** institution DEAD is inactive but has term rates for EUR/3M
- **THEN** the response does not include DEAD

#### Scenario: Institution without Term counterparty account excluded

- **WHEN** institution BNKCO has EUR/3M rates but no Term counterparty account at the requested LegalEntity
- **THEN** the response does not include BNKCO

#### Scenario: TradingClient returns only onboarded granted institutions

- **WHEN** all of the following hold:
  - `legalEntityCode` is PAR (a TradingClient);
  - hub institutions BNKCO and SGFR have EUR/3M rates;
  - only BNP has an active delegated grant with tenor 3M enabled;
  - `PAR` has onboarded BNP as `BNPLOC`, and it holds a Term counterparty account.
- **THEN** the response includes only `BNPLOC` with displayName `BNP Paribas via LOC` and the hub rate for BNP; BNKCO and SGFR are excluded

#### Scenario: TradingClient excludes a granted tenor the client has not enabled

- **WHEN** `legalEntityCode` is PAR, the grant `(BNP, PAR, EUR)` enables `3M`, but `PAR` has not client-enabled `EUR`/`3M` on `BNPLOC`
- **THEN** the response for EUR/3M does not include `BNPLOC`

#### Scenario: TradingClient excludes granted but not onboarded institution

- **WHEN** `legalEntityCode` is PAR, SGFR has an active grant for PAR/EUR with tenor 3M and EUR/3M rates, but `PAR` has not onboarded SGFR
- **THEN** the response does not include SGFR

#### Scenario: TradingClient excludes offboarded institution

- **WHEN** `legalEntityCode` is PAR and `PAR` has offboarded `BNPLOC`
- **THEN** the response does not include `BNPLOC`

#### Scenario: Missing legalEntityCode is rejected

- **WHEN** the PM requests term counterparties without `legalEntityCode`
- **THEN** the system returns `400 Bad Request`

---

### Requirement: OnCall counterparties open to new business with segment rates for PM's valueDate

`GET /api/v1/order-creation/oncall/counterparties?legalEntityCode={legalEntityCode}&currency={currency}&noticePeriod={noticePeriod}&valueDate={valueDate}` SHALL return institutions with on-call rate segments covering the given `valueDate`, scoped by `legalEntityCode`. Institutions SHALL hold an OnCall counterparty account:

- **TradingHub:** active hub-native institutions with a segment covering the date and an OnCall counterparty account.
- **TradingClient:** only the client's **onboarded institutions** that are not offboarded, hold an OnCall counterparty account, and whose **effective enablement** (active grant ∩ client enablement) includes the currency and notice period. Their rates SHALL be sourced from the connected hub's linked native institution. The linked hub institution SHALL also hold an OnCall counterparty account.

This list feeds new business, so closed-to-new-business institutions are excluded. Decrease and Redemption are unaffected: in contract-number shortcut mode the PM widget may confirm the locked contract institution without a rate row (per `pm-order-creation-widget`).

Segment coverage: `valueDate <= requested valueDate <= segment endDate` with status `VALID` or `PENDING_CONFIRMATION`. Each entry SHALL include `institutionCode`, `displayName`, `rate`, `rateDate` (the segment's value date), and `indicative` (contextual). Results SHALL be sorted by rate descending (best rate first).

#### Scenario: Institution with VALID segment covering valueDate is included

- **WHEN** institution BNKCO has a VALID segment for EUR/24H with valueDate 2026-06-01 and endDate 2999-12-31 and rate 2.85
- **AND** the PM requests counterparties with valueDate 2026-06-09
- **THEN** the response includes BNKCO with rate 2.85

#### Scenario: Institution with PENDING_CONFIRMATION segment is included

- **WHEN** institution CDNRD has a PENDING_CONFIRMATION segment for EUR/24H covering the requested valueDate
- **THEN** the response includes CDNRD

#### Scenario: Institution with no segment covering valueDate is excluded

- **WHEN** institution SGFR has no segment covering the requested valueDate for EUR/24H
- **THEN** the response does not include SGFR

#### Scenario: TradingClient returns only onboarded granted institutions

- **WHEN** all of the following hold:
  - `legalEntityCode` is PAR (a TradingClient);
  - hub institutions BNKCO and SGFR have EUR/24H segments covering the requested valueDate;
  - only BNP has an active delegated grant with notice period 24H enabled;
  - `PAR` has onboarded BNP as `BNPLOC`, and it holds an OnCall counterparty account.
- **THEN** the response includes only `BNPLOC` with the hub segment rate for BNP; BNKCO and SGFR are excluded

#### Scenario: TradingClient excludes offboarded institution

- **WHEN** `legalEntityCode` is PAR and `PAR` has offboarded `BNPLOC`, which has a covering EUR/24H segment
- **THEN** the response does not include `BNPLOC`

#### Scenario: Missing legalEntityCode is rejected

- **WHEN** the PM requests oncall counterparties without `legalEntityCode`
- **THEN** the system returns `400 Bad Request`

## REMOVED Requirements

### Requirement: Term counterparties with latest rates sorted by best rate

**Reason**: Client counterparties come from onboarded institutions (thin proxies retired) and require a Term counterparty account.

**Migration**: Superseded by *Term counterparties open to new business with latest rates sorted by best rate*; the endpoint and response shape are unchanged.

### Requirement: OnCall counterparties with segment rates for PM's valueDate

**Reason**: Client counterparties come from onboarded institutions (thin proxies retired) and require an OnCall counterparty account.

**Migration**: Superseded by *OnCall counterparties open to new business with segment rates for PM's valueDate*; the endpoint and response shape are unchanged.
