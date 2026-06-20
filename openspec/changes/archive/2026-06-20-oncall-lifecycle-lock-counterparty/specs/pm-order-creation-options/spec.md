# pm-order-creation-options Specification (delta)

## MODIFIED Requirements

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
