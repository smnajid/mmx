# pm-order-creation-options Specification

## ADDED Requirements

### Requirement: Live contracts listing for a portfolio

`GET /api/v1/order-creation/contracts?portfolioNumber={portfolioNumber}&orderType={orderType}` SHALL return the **live** contracts for the given portfolio and order type. A contract is materialised by an **executed Subscription** order (its `generatedContractNumber`). Both `portfolioNumber` and `orderType` (`TERM` or `ON_CALL`) are required. Each returned `LiveContract` SHALL include `contractNumber`, `orderType`, `currency`, `valueDate`, and `originalAmount`; OnCall entries SHALL include `noticePeriod`; Term entries SHALL include `tenor` and `endDate`. The endpoint SHALL NOT require an `X-Trader-Id` header.

#### Scenario: OnCall contract with no redemption is live

- **WHEN** portfolio PF-001 has an executed OnCall Subscription with generatedContractNumber CT-00042 and no redemption order references CT-00042
- **AND** the PM requests `contracts?portfolioNumber=PF-001&orderType=ON_CALL`
- **THEN** the response includes CT-00042 with its currency and noticePeriod

#### Scenario: OnCall contract with an existing redemption is excluded

- **WHEN** portfolio PF-001 has an executed OnCall Subscription CT-00042 and a redemption order (received or executed, not cancelled) with sourceContractNumber CT-00042
- **THEN** the response does not include CT-00042

#### Scenario: OnCall contract with only a cancelled redemption stays live

- **WHEN** the only redemption referencing CT-00042 has status CANCELLED
- **THEN** the response includes CT-00042

#### Scenario: Term contract whose end date is in the future is live

- **WHEN** portfolio PF-001 has an executed Term Subscription CT-00100 with valueDate 2026-06-01 and tenor 3M (end date 2026-09-01) and today is 2026-06-13
- **AND** the PM requests `contracts?portfolioNumber=PF-001&orderType=TERM`
- **THEN** the response includes CT-00100 with tenor 3M and endDate 2026-09-01

#### Scenario: Matured Term contract is excluded

- **WHEN** an executed Term Subscription CT-00099 has an end date on or before today
- **THEN** the response does not include CT-00099

#### Scenario: Non-executed subscriptions are not contracts

- **WHEN** a Subscription order for the portfolio exists but has not been executed (no generatedContractNumber)
- **THEN** it does not appear in the live contracts response

#### Scenario: Request without X-Trader-Id succeeds

- **WHEN** the PM system calls the contracts endpoint without an X-Trader-Id header
- **THEN** the request succeeds (200) with the live contracts data

## MODIFIED Requirements

### Requirement: Order creation API is contract-first

All endpoints under `/api/v1/order-creation/` SHALL be defined in the canonical OpenAPI (`specs/002-trader-orders-views/contracts/openapi.yaml`) under tag `OrderCreation`. Prose mirror `api-v1.md` SHALL match. Generated server interfaces and controller implementation MUST align with the published contract. The surface comprises **ten** GET operations: currencies (term, oncall), operations (term, oncall), tenors (term), notice-periods (oncall), counterparties (term, oncall), contract-info (oncall), and live contracts listing.

#### Scenario: OpenAPI documents all order creation endpoints

- **WHEN** a consumer reads the OpenAPI specification
- **THEN** ten GET operations are defined under `/api/v1/order-creation/`, including `contracts` with `LiveContract` / `LiveContractsResponse` schemas alongside the existing currency, operation, tenor, notice-period, counterparty, and contract-info operations
