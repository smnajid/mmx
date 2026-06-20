# pm-order-creation-options Specification

## Purpose

Read-only REST API exposing available order creation options for the Portfolio Management wizard. Endpoints under `/api/v1/order-creation/` return currencies, operations (with minimum amounts), tenors/notice periods, and counterparties with rates — filtered at each step so only combinations with at least one available counterparty are presented. Includes contract-info lookup for OnCall lifecycle operations.

## Requirements

### Requirement: Term currencies filtered by counterparty availability

`GET /api/v1/order-creation/term/currencies` SHALL return only currencies where the managed currency is active, has at least one enabled tenor, and at least one active institution has a term rate for that currency (any trading date, any enabled tenor). The response SHALL include the `tradingDate` used for rate lookups (today's date).

#### Scenario: Active currency with term rates is included

- **WHEN** managed currency EUR is active with enabled tenors [1M, 3M] and institution BNKCO has a term rate for EUR/3M
- **THEN** the response includes EUR

#### Scenario: Active currency without any term rates is excluded

- **WHEN** managed currency CHF is active with enabled tenors [1M] but no institution has any term rate for CHF
- **THEN** the response does not include CHF

#### Scenario: Inactive currency is excluded

- **WHEN** managed currency JPY is inactive
- **THEN** the response does not include JPY regardless of existing term rates

#### Scenario: Currency with no enabled tenors is excluded

- **WHEN** managed currency GBP is active but has no enabled tenors (only notice periods)
- **THEN** the response does not include GBP

---

### Requirement: OnCall currencies filtered by counterparty availability

`GET /api/v1/order-creation/oncall/currencies` SHALL return only currencies where the managed currency is active, has at least one enabled notice period, and at least one active institution has an open on-call rate segment (status `VALID` or `PENDING_CONFIRMATION`) for that currency and at least one enabled notice period.

#### Scenario: Active currency with open on-call segment is included

- **WHEN** managed currency EUR is active with enabled notice periods [24H] and institution BNKCO has an open VALID segment for EUR/24H
- **THEN** the response includes EUR

#### Scenario: Active currency with only CANCELED segments is excluded

- **WHEN** managed currency USD has only CANCELED on-call segments for all institutions
- **THEN** the response does not include USD

---

### Requirement: Term operations return allowed operations with minimum amounts

`GET /api/v1/order-creation/term/operations?currency={currency}` SHALL return the operations allowed for Term orders with the minimum amount for each operation. For Term, only SUBSCRIPTION is allowed. The minimum amount SHALL come from `ManagedCurrency.minSubscriptionAmount` for the given currency.

#### Scenario: Term operations for a valid currency

- **WHEN** the PM requests term operations for currency EUR with minSubscriptionAmount 500000
- **THEN** the response contains exactly one entry: operation SUBSCRIPTION with minAmount 500000

#### Scenario: Term operations for unknown currency returns empty

- **WHEN** the PM requests term operations for currency XYZ which is not in the managed catalog
- **THEN** the response returns an error or empty result

---

### Requirement: OnCall operations return allowed operations with minimum amounts

`GET /api/v1/order-creation/oncall/operations?currency={currency}` SHALL return the operations allowed for OnCall orders with the minimum amount for each. SUBSCRIPTION uses `minSubscriptionAmount`; INCREASE, DECREASE, and REDEMPTION use `minIncreaseDecreaseAmount`. The PM application filters which operations to show based on its context (portfolio-only → SUBSCRIPTION; portfolio+contract → INCREASE, DECREASE, REDEMPTION).

#### Scenario: OnCall operations for a valid currency

- **WHEN** the PM requests oncall operations for currency EUR with minSubscriptionAmount 500000 and minIncreaseDecreaseAmount 100000
- **THEN** the response contains four entries: SUBSCRIPTION (min 500000), INCREASE (min 100000), DECREASE (min 100000), REDEMPTION (min 100000)

---

### Requirement: Term tenors filtered by counterparty availability

`GET /api/v1/order-creation/term/tenors?currency={currency}` SHALL return only tenors that are enabled for the currency in the managed currency catalog AND where at least one active institution has a term rate for that currency and tenor (any trading date).

#### Scenario: Enabled tenor with rates is included

- **WHEN** currency EUR has enabled tenors [1M, 3M, 6M] and institutions have term rates for EUR/1M and EUR/3M but not EUR/6M
- **THEN** the response includes 1M and 3M but not 6M

#### Scenario: Disabled tenor is excluded even if rates exist

- **WHEN** currency EUR has enabled tenors [1M, 3M] and an institution has a rate for EUR/1Y
- **THEN** the response does not include 1Y because it is not in enabledTenors

---

### Requirement: OnCall notice periods filtered by counterparty availability

`GET /api/v1/order-creation/oncall/notice-periods?currency={currency}` SHALL return only notice periods that are enabled for the currency in the managed currency catalog AND where at least one active institution has an open on-call rate segment (VALID or PENDING_CONFIRMATION) for that currency and notice period.

#### Scenario: Enabled notice period with open segment is included

- **WHEN** currency EUR has enabled notice periods [24H, 48H] and institution BNKCO has an open VALID segment for EUR/24H but no institution has a segment for EUR/48H
- **THEN** the response includes 24H but not 48H

---

### Requirement: Term counterparties with latest rates sorted by best rate

`GET /api/v1/order-creation/term/counterparties?currency={currency}&tenor={tenor}` SHALL return active institutions that have at least one term rate for the given currency and tenor. For each institution, the system SHALL return the **latest** available rate (most recent trading date). Each entry SHALL include `institutionCode`, `displayName`, `rate`, `rateDate` (the trading date of the rate), and `indicative` (true when `rateDate` is before today). Results SHALL be sorted by rate descending (best rate first).

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

---

### Requirement: OnCall counterparties with segment rates for PM's valueDate

`GET /api/v1/order-creation/oncall/counterparties?currency={currency}&noticePeriod={noticePeriod}&valueDate={valueDate}` SHALL return active institutions that have an on-call rate segment covering the given `valueDate` (segment `valueDate <= requested valueDate <= segment endDate`) with status `VALID` or `PENDING_CONFIRMATION` for the given currency and notice period. Each entry SHALL include `institutionCode`, `displayName`, `rate`, `rateDate` (the segment's value date), and `indicative` (contextual). Results SHALL be sorted by rate descending (best rate first).

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

---

### Requirement: Contract-info lookup for OnCall lifecycle operations

`GET /api/v1/order-creation/oncall/contract-info?contractNumber={contractNumber}` SHALL look up the original executed Subscription order in mmx by `generatedContractNumber` and return the order's `currency`, `noticePeriod`, `institutionCode`, and `counterparty` (display name from the executed subscription). The system SHALL return 404 when no matching executed Subscription order is found.

#### Scenario: Valid contract number returns currency, notice period, and institution

- **WHEN** the PM requests contract info for CT-00042 and an executed OnCall Subscription order exists with generatedContractNumber CT-00042, currency EUR, noticePeriod 24H, institutionCode BNKCO, and counterparty BankCo
- **THEN** the response contains currency EUR, noticePeriod 24H, institutionCode BNKCO, and counterparty BankCo

#### Scenario: Unknown contract number returns 404

- **WHEN** the PM requests contract info for CT-99999 and no executed Subscription order has that contract number
- **THEN** the system returns 404

#### Scenario: Non-subscription order is not matched

- **WHEN** the PM requests contract info for CT-00043 and the only order with that contract number is an executed Increase (not a Subscription)
- **THEN** the system returns 404 (only original Subscription allocations are matched)

---

---

### Requirement: Live contracts listing for a portfolio

`GET /api/v1/order-creation/contracts?portfolioNumber={portfolioNumber}&orderType={orderType}` SHALL return the **live** contracts for the given portfolio and order type. A contract is materialised by an **executed Subscription** order (its `generatedContractNumber`). Both `portfolioNumber` and `orderType` (`TERM` or `ON_CALL`) are required. Each returned `LiveContract` SHALL include `contractNumber`, `orderType`, `currency`, `valueDate`, and `originalAmount`; OnCall entries SHALL include `noticePeriod`; Term entries SHALL include `tenor` and `endDate`. The endpoint SHALL NOT require an `X-Trader-Id` header.

#### Scenario: OnCall contract with no redemption is live

- **WHEN** portfolio PF-001 has an executed OnCall Subscription with generatedContractNumber CT-00042 and no redemption order references CT-00042
- **AND** the PM requests `contracts?portfolioNumber=PF-001&orderType=ON_CALL`
- **THEN** the response includes CT-00042 with its currency and noticePeriod

#### Scenario: OnCall contract with an existing redemption is excluded

- **WHEN** portfolio PF-001 has an executed OnCall Subscription CT-00042 and a redemption order (received or executed, not cancelled) with sourceContractNumber CT-00042
- **THEN** the response does not include CT-00042

#### Scenario: Term contract whose end date is in the future is live

- **WHEN** portfolio PF-001 has an executed Term Subscription CT-00100 with valueDate 2026-06-01 and tenor 3M (end date 2026-09-01) and today is 2026-06-13
- **AND** the PM requests `contracts?portfolioNumber=PF-001&orderType=TERM`
- **THEN** the response includes CT-00100 with tenor 3M and endDate 2026-09-01

#### Scenario: Request without X-Trader-Id succeeds

- **WHEN** the PM system calls the contracts endpoint without an X-Trader-Id header
- **THEN** the request succeeds (200) with the live contracts data

---

### Requirement: Order creation API is contract-first

All endpoints under `/api/v1/order-creation/` SHALL be defined in the canonical OpenAPI (`specs/002-trader-orders-views/contracts/openapi.yaml`) under tag `OrderCreation`. Prose mirror `api-v1.md` SHALL match. Generated server interfaces and controller implementation MUST align with the published contract. The surface comprises **ten** GET operations: currencies (term, oncall), operations (term, oncall), tenors (term), notice-periods (oncall), counterparties (term, oncall), contract-info (oncall), and live contracts listing.

#### Scenario: OpenAPI documents all order creation endpoints

- **WHEN** a consumer reads the OpenAPI specification
- **THEN** ten GET operations are defined under `/api/v1/order-creation/`, including `contracts` with `LiveContract` / `LiveContractsResponse` schemas alongside the existing currency, operation, tenor, notice-period, counterparty, and contract-info operations

---

### Requirement: No authentication required for order creation options

Order creation endpoints SHALL NOT require `X-Trader-Id` header. They are consumed by the external Portfolio Management system, not by traders.

#### Scenario: Request without X-Trader-Id succeeds

- **WHEN** the PM system calls any order creation endpoint without X-Trader-Id header
- **THEN** the request succeeds (200) with the appropriate options data
