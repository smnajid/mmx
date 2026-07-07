## MODIFIED Requirements

### Requirement: Lifecycle intake institution must match source contract

For OnCall orders with `orderOperation` ∈ {INCREASE, DECREASE, REDEMPTION} and a non-null `sourceContractNumber`, the system SHALL look up the original executed Subscription order by `generatedContractNumber` equal to `sourceContractNumber`. The submitted `institutionCode` MUST equal the subscription order's persisted `institutionCode`. If they differ, intake SHALL be rejected with a clear error and no order SHALL be created. This requirement applies at **both TradingHub intake and TradingClient (routed) intake**; the routing path SHALL NOT bypass it. On the routed path, the submitted `institutionCode` is the TradingClient's thin-proxy institution and the lookup matches it against the source subscription's persisted proxy `institutionCode`.

#### Scenario: Lifecycle intake with matching institution succeeds

- **WHEN** Portfolio Management submits an OnCall INCREASE with sourceContractNumber CT-00042 and institutionCode BNKCO, and the executed Subscription for CT-00042 has institutionCode BNKCO
- **THEN** intake succeeds

#### Scenario: Lifecycle intake with mismatched institution is rejected

- **WHEN** Portfolio Management submits an OnCall INCREASE with sourceContractNumber CT-00042 and institutionCode SGFR, but the executed Subscription for CT-00042 has institutionCode BNKCO
- **THEN** the system rejects intake with a clear institution/contract mismatch error
- **AND** no order is created

#### Scenario: Routed TradingClient lifecycle intake with mismatched institution is rejected

- **WHEN** Portfolio Management submits an OnCall INCREASE for TradingClient `PAR` with sourceContractNumber CT-00042 and proxy institutionCode `BNP-VIA-LOC`, but the executed Subscription for CT-00042 has proxy institutionCode `SGFR-VIA-LOC`
- **THEN** the system rejects intake with a clear institution/contract mismatch error
- **AND** no order is created (no client-side order, no hub-side order)

#### Scenario: Subscription intake is not subject to contract institution match

- **WHEN** Portfolio Management submits an OnCall SUBSCRIPTION with any active institutionCode and no sourceContractNumber
- **THEN** the lifecycle contract institution rule does not apply (normal institution validation only)
