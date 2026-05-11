# execution-contract-number Specification

## Purpose

Defines when Money Market execution allocates a **new** `generatedContractNumber` versus reusing intake `sourceContractNumber` for lifecycle operations; subscription intake discards optional PM `sourceContractNumber`; HTTP documentation semantics for `generatedContractNumber`.

## Requirements

### Requirement: Subscription execution allocates a new contract number

When `orderOperation` is SUBSCRIPTION, the system SHALL obtain the execution contract number by invoking `ReferenceGenerator.generateContractNumber()` (or equivalent) and SHALL persist the result as execution `generatedContractNumber` (HTTP field name unchanged).

#### Scenario: Trader executes a subscription

- **WHEN** an assigned Trader executes an order whose `orderOperation` is SUBSCRIPTION
- **THEN** the system invokes `generateContractNumber()` for that execution and stores the returned value on execution details as `generatedContractNumber`

#### Scenario: Subscription ignores intake sourceContractNumber for execution identity

- **WHEN** an order has `orderOperation` SUBSCRIPTION and intake supplied a non-null `sourceContractNumber` (optional PM payload field)
- **THEN** the system MUST NOT use that intake value as the execution `generatedContractNumber` and MUST allocate a new contract number at execute according to subscription rules

### Requirement: Subscription reception does not persist sourceContractNumber

On receive, when `orderOperation` is SUBSCRIPTION, the system MUST NOT persist `sourceContractNumber`. If the intake payload includes `sourceContractNumber`, the system MUST discard it at reception so the stored order has null `sourceContractNumber`.

#### Scenario: Subscription receive drops PM-supplied source

- **WHEN** Portfolio Management submits a Subscription order with a non-null `sourceContractNumber` in the request body
- **THEN** the persisted order has null `sourceContractNumber` and subsequent reads do not expose that PM-supplied value as source contract identity

### Requirement: Lifecycle execution reuses intake contract number

When `orderOperation` is INCREASE, DECREASE, or REDEMPTION, the system SHALL NOT call `ReferenceGenerator.generateContractNumber()` for that execution. The system SHALL persist execution `generatedContractNumber` equal to the order's intake `sourceContractNumber` (same underlying MM contract reference).

#### Scenario: Trader executes a lifecycle order

- **WHEN** an assigned Trader executes an order whose `orderOperation` is INCREASE, DECREASE, or REDEMPTION and the order has a non-null `sourceContractNumber`
- **THEN** the system does not invoke `generateContractNumber()` and stores `generatedContractNumber` equal to `sourceContractNumber`

### Requirement: Lifecycle intake requires sourceContractNumber

For `orderOperation` ∈ {INCREASE, DECREASE, REDEMPTION}, the system SHALL reject receiving the order when `sourceContractNumber` is missing or not usable as the existing contract reference (blank).

#### Scenario: Lifecycle receive without source fails

- **WHEN** an order is submitted with `orderOperation` INCREASE, DECREASE, or REDEMPTION and without a valid `sourceContractNumber`
- **THEN** the system rejects the receive operation and does not create a persisted order

### Requirement: Execute fails when lifecycle sourceContractNumber is missing

If execute is attempted for an order with `orderOperation` ∈ {INCREASE, DECREASE, REDEMPTION} and persisted `sourceContractNumber` is null, the system SHALL reject execution with a clear error and SHALL NOT persist a partial execution.

#### Scenario: Lifecycle execute with null source

- **WHEN** execute is requested for a lifecycle order whose persisted `sourceContractNumber` is null
- **THEN** the system rejects the execute operation and the order remains not EXECUTED from that attempt

### Requirement: HTTP documentation describes generatedContractNumber semantics

The canonical trader/product API documentation (OpenAPI `description` and `api-v1.md` prose for `generatedContractNumber`) SHALL state that for SUBSCRIPTION the value is allocated at execution, and for INCREASE, DECREASE, and REDEMPTION the value equals the intake `sourceContractNumber` (field name unchanged).

#### Scenario: Contract prose matches behaviour

- **WHEN** a consumer reads `generatedContractNumber` in `openapi.yaml` or `api-v1.md`
- **THEN** the text reflects subscription vs lifecycle semantics without requiring a breaking rename of the field
